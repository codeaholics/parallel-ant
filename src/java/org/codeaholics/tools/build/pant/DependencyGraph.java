package org.codeaholics.tools.build.pant;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.tools.ant.Target;

public class DependencyGraph {
    private final Map<String, DependencyGraphEntry> dependencyGraphEntries = new HashMap<String, DependencyGraphEntry>();
    private final Map<String, Target> targets;
    private final DependencyGraphEntryFactory dependencyGraphEntryFactory;

    public DependencyGraph(final Map<String, Target> targets,
                           final DependencyGraphEntryFactory dependencyGraphEntryFactory) {
        this.targets = targets;
        this.dependencyGraphEntryFactory = dependencyGraphEntryFactory;
    }

    public DependencyGraphEntry buildDependencies(final Target target) {
        final String targetName = target.getName();

        if (dependencyGraphEntries.containsKey(targetName)) {
            // already done/in progress
            return dependencyGraphEntries.get(targetName);
        }

        final DependencyGraphEntry dependencyGraphEntry = dependencyGraphEntryFactory.create(target);
        dependencyGraphEntries.put(targetName, dependencyGraphEntry);

        processDependencies(dependencyGraphEntry);

        return dependencyGraphEntry;
    }

    private void processDependencies(final DependencyGraphEntry dependencyGraphEntry) {
        @SuppressWarnings("unchecked")
        final Enumeration<String> dependencies = dependencyGraphEntry.getTarget().getDependencies();

        while (dependencies.hasMoreElements()) {
            final String dependency = dependencies.nextElement();

            dependencyGraphEntry.addPredecessor(dependency);
            final DependencyGraphEntry predecessorDependencyGraphEntry = buildDependencies(targets.get(dependency));
            predecessorDependencyGraphEntry.addSuccessor(dependencyGraphEntry.getTarget().getName());
        }
    }

    public List<DependencyGraphEntry> discoverAllSchedulableTargets() {
        final List<DependencyGraphEntry> schedulableTargets = new LinkedList<DependencyGraphEntry>();

        for (final DependencyGraphEntry dependencyGraphEntry: dependencyGraphEntries.values()) {
            if ((dependencyGraphEntry.getState() == TargetState.WAITING) &&
                    areAllPredecessorsComplete(dependencyGraphEntry)) {
                schedulableTargets.add(dependencyGraphEntry);
            }
        }

        return schedulableTargets;
    }

    private boolean areAllPredecessorsComplete(final DependencyGraphEntry dependencyGraphEntry) {
        for (final String predecessor: dependencyGraphEntry.getPredecessors()) {
            if (dependencyGraphEntries.get(predecessor).getState() != TargetState.COMPLETE) {
                return false;
            }
        }

        return true;
    }

    public void dump() {
        System.out.println("=========================================================");
        for (final DependencyGraphEntry dependencyGraphEntry: dependencyGraphEntries.values()) {
            System.out.println("Target name:  " + dependencyGraphEntry.getTarget().getName());
            System.out.print("Predecessors: ");
            for (final String predecessor: dependencyGraphEntry.getPredecessors()) {
                System.out.print(predecessor + ", ");
            }
            System.out.println();
            System.out.print("Successors:   ");
            for (final String successor: dependencyGraphEntry.getSuccessors()) {
                System.out.print(successor + ", ");
            }
            System.out.println();
            System.out.println("=========================================================");
        }
    }
}
