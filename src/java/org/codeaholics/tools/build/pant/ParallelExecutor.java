package org.codeaholics.tools.build.pant;

/*
 *   Copyright 2010 Danny Yates
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Executor;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.helper.SingleCheckExecutor;

public class ParallelExecutor implements Executor {
    private static final SingleCheckExecutor SUB_EXECUTOR = new SingleCheckExecutor();
    private static final String PRIVATE_TARGET_PREFIX = "pant:";
    private static final String PRE_PHASE_TARGET = PRIVATE_TARGET_PREFIX + "pre-phase";
    private static final int DEFAULT_THREAD_COUNT = 2;

    private final List<String> prePhaseTargets = new LinkedList<String>();

    private DependencyGraph dependencyGraph;
    private DependencyGraphEntry rootDependencyGraphEntry;
    private ExecutorServiceFactory executorServiceFactory = new ExecutorServiceFactoryImpl();
    private AntWrapper antWrapper = new AntWrapperImpl();
    private ExecutorService executorService;

    private int queued;
    private int started;
    private int finished;

    private boolean isPrePhase;

    @Override
    public void executeTargets(final Project project, final String[] targetNames) throws BuildException {
        @SuppressWarnings("unchecked")
        final Map<String, Target> targetsByName = project.getTargets();

        // check for cycles and unknown targets
        antWrapper.topologicalSortProject(project, targetNames, true);

        configure(targetsByName);

        verifyPrePhaseTargets(targetsByName);

        BuildException thrownException = null;

        for (final String targetName: targetNames) {
            try {
                if (targetName.startsWith(PRIVATE_TARGET_PREFIX)) {
                    throw new CannotExecutePrivateTargetException(targetName);
                }

                executeTarget(targetsByName.get(targetName), targetsByName);
            } catch (final BuildException ex) {
                if (project.isKeepGoingMode()) {
                    thrownException = ex;
                } else {
                    throw ex;
                }
            }
        }

        if (thrownException != null) {
            throw thrownException;
        }
    }

    public void setExecutorServiceFactory(final ExecutorServiceFactory executorServiceFactory) {
        this.executorServiceFactory = executorServiceFactory;
    }

    public void setAntWrapper(final AntWrapper antWrapper) {
        this.antWrapper = antWrapper;
    }

    private void executeTarget(final Target target, final Map<String, Target> targetsByName) {
        final DependencyGraphEntryFactory dependencyGraphEntryFactory =
            new DependencyGraphEntryFactoryImpl(getTargetExecutionNotifier(), antWrapper);
        dependencyGraph = new DependencyGraph(targetsByName, prePhaseTargets, dependencyGraphEntryFactory);
        rootDependencyGraphEntry = dependencyGraph.buildDependencies(target);
        isPrePhase = true;

        executorService = executorServiceFactory.create(getNumberOfThreads());

        scheduleMore();

        try {
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (final InterruptedException e) {
            // ignore
        }
    }

    private int getNumberOfThreads() {
        return DEFAULT_THREAD_COUNT;
    }

    private void scheduleMore() {
        final List<DependencyGraphEntry> schedulableTargets = dependencyGraph.discoverAllSchedulableTargets();

        if (isPrePhase) {
            final List<DependencyGraphEntry> schedulablePrePhaseTargets = new LinkedList<DependencyGraphEntry>();
            for (final DependencyGraphEntry dependencyGraphEntry: schedulableTargets) {
                if (dependencyGraphEntry.isPrePhase()) {
                    schedulablePrePhaseTargets.add(dependencyGraphEntry);
                }
            }

            if (schedulablePrePhaseTargets.size() == 0 && queued == finished) {
                // All queued tasks have finished and no new pre-tasks found
                isPrePhase = false;
            } else {
                // More pre-phase work to do. Replace schedulable targets with filtered list
                schedulableTargets.clear();
                schedulableTargets.addAll(schedulablePrePhaseTargets);
            }
        }

        for (final DependencyGraphEntry dependencyGraphEntry: schedulableTargets) {
            dependencyGraphEntry.setState(TargetState.QUEUED);
        }

        for (final DependencyGraphEntry dependencyGraphEntry: schedulableTargets) {
            queued++;
            executorService.submit(dependencyGraphEntry);
        }
    }

    private TargetExecutionNotifier getTargetExecutionNotifier() {
        return new TargetExecutionNotifier() {
            @Override
            public synchronized void notifyStarting(final DependencyGraphEntry dependencyGraphEntry) {
                dependencyGraphEntry.setState(TargetState.RUNNING);
                started++;
            }

            @Override
            public synchronized void notifyComplete(final DependencyGraphEntry dependencyGraphEntry) {
                dependencyGraphEntry.setState(TargetState.COMPLETE);
                finished++;

                scheduleMore();

                if (dependencyGraphEntry == rootDependencyGraphEntry) {
                    executorService.shutdown();
                }
            }
        };
    }

    private void configure(final Map<String, Target> targetsByName) {
        final Target prePhaseConfig = targetsByName.get(PRE_PHASE_TARGET);

        if (prePhaseConfig != null) {
            configurePrePhase(prePhaseConfig);
        }

        for (final Entry<String, Target> entry: targetsByName.entrySet()) {
            final String targetName = entry.getKey();
            final Target target = entry.getValue();

            // check for unknown private targets
            if (targetName.startsWith(PRIVATE_TARGET_PREFIX)) {
                if (!targetName.equals(PRE_PHASE_TARGET)) {
                    throw new UnknownPrivateTargetException(target);
                }

                // ensure private target does not have any tasks
                final Task[] tasks = target.getTasks();
                if (tasks != null && tasks.length > 0) {
                    throw new PrivateTargetCannotHaveTasksException(target);
                }
            }

            // ensure nothing has a private target as a dependency
            @SuppressWarnings("unchecked")
            final Enumeration<String> dependencies = target.getDependencies();
            while (dependencies.hasMoreElements()) {
                if (dependencies.nextElement().startsWith(PRIVATE_TARGET_PREFIX)) {
                    throw new CannotDependOnPrivateTargetException(targetName);
                }
            }
        }
    }

    private void configurePrePhase(final Target prePhaseConfig) {
        @SuppressWarnings("unchecked")
        final Enumeration<String> dependencies = prePhaseConfig.getDependencies();
        while (dependencies.hasMoreElements()) {
            prePhaseTargets.add(dependencies.nextElement());
        }
    }

    private void verifyPrePhaseTargets(final Map<String, Target> targetsByName) {
        for (final String prePhaseTargetName: prePhaseTargets) {
            final Target prePhaseTarget = targetsByName.get(prePhaseTargetName);

            @SuppressWarnings("unchecked")
            final Enumeration<String> dependencies = prePhaseTarget.getDependencies();
            while (dependencies.hasMoreElements()) {
                if (!prePhaseTargets.contains(dependencies.nextElement())) {
                    throw new PrePhaseTargetCanOnlyDependOnPrePhaseTargetsException(prePhaseTargetName);
                }
            }
        }
    }

    @Override
    public Executor getSubProjectExecutor() {
        return SUB_EXECUTOR;
    }
}