package org.codeaholics.tools.build.pant;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import org.apache.tools.ant.Target;

public class DependencyTree {
    private final Map<String, TargetWrapper> targets = new HashMap<String, TargetWrapper>();

    public DependencyTree(final Target target, final Map<String, Target> targetsByName) {
        buildDependencies(target, targetsByName);
    }

    private synchronized TargetWrapper buildDependencies(final Target target, final Map<String, Target> targetsByName) {
        if (targets.containsKey(target.getName())) {
            // already done/in progress
            return targets.get(target.getName());
        }

        final TargetWrapper targetWrapper = new TargetWrapper(target, this);
        targets.put(target.getName(), targetWrapper);

        for (final Enumeration<String> dependencies = target.getDependencies(); dependencies.hasMoreElements(); ) {
            final String dependency = dependencies.nextElement();

            targetWrapper.addPredecessor(dependency);
            final TargetWrapper predecessorTargetWrapper = buildDependencies(targetsByName.get(dependency), targetsByName);
            predecessorTargetWrapper.addSuccessor(target.getName());
        }

        return targetWrapper;
    }

    public synchronized void submitAllReadyTargets(final Executor executor) {
        for (final TargetWrapper targetWrapper : targets.values()) {
            if (targetWrapper.getState() == TargetState.WAITING) {
                submitTargetIfAllPredecessorsComplete(targetWrapper, executor);
            }
        }
    }

    private synchronized void submitTargetIfAllPredecessorsComplete(final TargetWrapper targetWrapper, final Executor executor) {
        for (final String predecessor : targetWrapper.getPredecessors()) {
            if (targets.get(predecessor).getState() != TargetState.COMPLETE) {
                return;
            }
        }

        targetWrapper.setState(TargetState.QUEUED);
        executor.execute(targetWrapper);
    }

    public synchronized void dump() {
        for (final TargetWrapper targetWrapper : targets.values()) {
            System.out.println("Target name:  " + targetWrapper.getTarget().getName());
            System.out.print("Predecessors: ");
            for (final String predecessor : targetWrapper.getPredecessors()) {
                System.out.print(predecessor + ", ");
            }
            System.out.println();
            System.out.print("Successors:   ");
            for (final String successor : targetWrapper.getSuccessors()) {
                System.out.print(successor + ", ");
            }
            System.out.println();
            System.out.println("=========================================================");
        }
    }
}
