package org.codeaholics.tools.build.pant;

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
            new DependencyGraphEntryFactory(getTargetExecutionNotifier(), targetExecutor);
        dependencyGraph = new DependencyGraph(targetsByName, dependencyGraphEntryFactory);
        rootDependencyGraphEntry = dependencyGraph.buildDependencies(target);

        executorService = executorServiceFactory.create(2);

        scheduleMore();

        try {
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (final InterruptedException e) {
            // ignore
        }
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