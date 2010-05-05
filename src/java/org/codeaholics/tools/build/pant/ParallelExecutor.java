package org.codeaholics.tools.build.pant;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Executor;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.helper.SingleCheckExecutor;

public class ParallelExecutor implements Executor {
    private static final SingleCheckExecutor SUB_EXECUTOR = new SingleCheckExecutor();

    private DependencyTree dependencyTree;
    private TargetWrapper rootTargetWrapper;
    private ExecutorService executorService;

    private int queued;
    private int started;
    private int finished;

    @SuppressWarnings("unchecked")
    @Override
    public void executeTargets(final Project project, final String[] targetNames) throws BuildException {
        final Map<String, Target> targetsByName = project.getTargets();

        for (final String targetName : targetNames) {
            executeTarget(targetsByName.get(targetName), targetsByName);
        }
    }

    private void executeTarget(final Target target, final Map<String, Target> targetsByName) {
        final TargetWrapperFactory targetWrapperFactory = new TargetWrapperFactory(getTargetExecutionNotifier());
        dependencyTree = new DependencyTree(targetsByName, targetWrapperFactory);
        rootTargetWrapper = dependencyTree.buildDependencies(target);
        dependencyTree.dump();

        executorService = Executors.newFixedThreadPool(2);

        scheduleMore();

        try {
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (final InterruptedException e) {
            // ignore
        }
    }

    private synchronized void scheduleMore() {
        System.out.println("Immediately schedulable tasks: " + dependencyTree.discoverAllSchedulableTargets());
        for (final TargetWrapper targetWrapper : dependencyTree.discoverAllSchedulableTargets()) {
            executorService.submit(targetWrapper);
            targetWrapper.setState(TargetState.QUEUED);
            queued++;
            System.out.println("  Queued: " + targetWrapper);
        }
    }

    private TargetExecutionNotifier getTargetExecutionNotifier() {
        return new TargetExecutionNotifier() {
            @Override
            public void notifyStarting(final TargetWrapper targetWrapper) {
                targetWrapper.setState(TargetState.RUNNING);
                started++;
                System.out.println("  Starting: " + targetWrapper);
            }

            @Override
            public void notifyComplete(final TargetWrapper targetWrapper) {
                targetWrapper.setState(TargetState.COMPLETE);
                finished++;
                System.out.println("  Completed: " + targetWrapper);

                scheduleMore();

                if (targetWrapper == rootTargetWrapper) {
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