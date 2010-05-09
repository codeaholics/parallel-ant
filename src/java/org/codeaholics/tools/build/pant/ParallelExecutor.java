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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Executor;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.helper.SingleCheckExecutor;

public class ParallelExecutor implements Executor {
    private static final SingleCheckExecutor SUB_EXECUTOR = new SingleCheckExecutor();
    private static final int DEFAULT_THREAD_COUNT = 2;

    private DependencyGraph dependencyGraph;
    private DependencyGraphEntry rootDependencyGraphEntry;
    private ExecutorServiceFactory executorServiceFactory = new ExecutorServiceFactoryImpl();
    private TargetExecutor targetExecutor = new TargetExecutorImpl();
    private ExecutorService executorService;

    private int queued;
    private int started;
    private int finished;

    @SuppressWarnings("unchecked")
    @Override
    public void executeTargets(final Project project, final String[] targetNames) throws BuildException {
        final Map<String, Target> targetsByName = project.getTargets();

        BuildException thrownException = null;

        for (final String targetName: targetNames) {
            try {
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

    public void setTargetExecutor(final TargetExecutor targetExecutor) {
        this.targetExecutor = targetExecutor;
    }

    private void executeTarget(final Target target, final Map<String, Target> targetsByName) {
        final DependencyGraphEntryFactory dependencyGraphEntryFactory =
            new DependencyGraphEntryFactoryImpl(getTargetExecutionNotifier(), targetExecutor);
        dependencyGraph = new DependencyGraph(targetsByName, dependencyGraphEntryFactory);
        rootDependencyGraphEntry = dependencyGraph.buildDependencies(target);

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

    private synchronized void scheduleMore() {
        final List<DependencyGraphEntry> schedulableTargets = dependencyGraph.discoverAllSchedulableTargets();

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
            public void notifyStarting(final DependencyGraphEntry dependencyGraphEntry) {
                dependencyGraphEntry.setState(TargetState.RUNNING);
                started++;
            }

            @Override
            public void notifyComplete(final DependencyGraphEntry dependencyGraphEntry) {
                dependencyGraphEntry.setState(TargetState.COMPLETE);
                finished++;

                scheduleMore();

                if (dependencyGraphEntry == rootDependencyGraphEntry) {
                    executorService.shutdown();
                }
            }
        };
    }

    @Override
    public Executor getSubProjectExecutor() {
        return SUB_EXECUTOR;
    }
}