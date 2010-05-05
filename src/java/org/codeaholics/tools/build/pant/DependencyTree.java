package org.codeaholics.tools.build.pant;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.tools.ant.Target;

public class DependencyTree {
    private final Map<String, TargetWrapper> targets = new HashMap<String, TargetWrapper>();
    private final TargetWrapper rootTargetWrapper;

    public DependencyTree(final Target target, final Map<String, Target> targetsByName,
                          final TargetWrapperFactory targetWrapperFactory) {
        rootTargetWrapper = buildDependencies(target, targetsByName, targetWrapperFactory);
    }

    private TargetWrapper buildDependencies(final Target target, final Map<String, Target> targetsByName,
                                            final TargetWrapperFactory targetWrapperFactory) {
        if (targets.containsKey(target.getName())) {
            // already done/in progress
            return targets.get(target.getName());
        }

        final TargetWrapper targetWrapper = targetWrapperFactory.create(target);
        targets.put(target.getName(), targetWrapper);

        processDependencies(targetWrapper, targetsByName, targetWrapperFactory);

        return targetWrapper;
    }

    private void processDependencies(final TargetWrapper targetWrapper, final Map<String, Target> targetsByName,
                                     final TargetWrapperFactory targetWrapperFactory) {
        @SuppressWarnings("unchecked")
        final Enumeration<String> dependencies = targetWrapper.getTarget().getDependencies();

        while (dependencies.hasMoreElements()) {
            final String dependency = dependencies.nextElement();

            targetWrapper.addPredecessor(dependency);
            final TargetWrapper predecessorTargetWrapper = buildDependencies(targetsByName.get(dependency), targetsByName, targetWrapperFactory);
            predecessorTargetWrapper.addSuccessor(targetWrapper.getTarget().getName());
        }
    }

    public List<TargetWrapper> discoverAllSchedulableTargets() {
        final List<TargetWrapper> schedulableTargets = new LinkedList<TargetWrapper>();

        for (final TargetWrapper targetWrapper : targets.values()) {
            if ((targetWrapper.getState() == TargetState.WAITING) && areAllPredecessorsComplete(targetWrapper)) {
                schedulableTargets.add(targetWrapper);
            }
        }

        return schedulableTargets;
    }

    private boolean areAllPredecessorsComplete(final TargetWrapper targetWrapper) {
        for (final String predecessor : targetWrapper.getPredecessors()) {
            if (targets.get(predecessor).getState() != TargetState.COMPLETE) {
                return false;
            }
        }

        return true;
    }

    public void dump() {
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
