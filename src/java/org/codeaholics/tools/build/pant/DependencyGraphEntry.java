package org.codeaholics.tools.build.pant;

import java.util.HashSet;
import java.util.Set;

import org.apache.tools.ant.Target;

public class DependencyGraphEntry implements Runnable {
    private final Target target;
    private final TargetExecutionNotifier executionNotifier;
    private final TargetExecutor targetExecutor;
    private final Set<String> predecessors = new HashSet<String>();
    private final Set<String> successors = new HashSet<String>();

    private TargetState state = TargetState.WAITING;

    public DependencyGraphEntry(final Target target, final TargetExecutionNotifier executionNotifier,
                                final TargetExecutor targetExecutor) {
        this.target = target;
        this.executionNotifier = executionNotifier;
        this.targetExecutor = targetExecutor;
    }

    public Target getTarget() {
        return target;
    }

    public void addPredecessor(final String predecessor) {
        predecessors.add(predecessor);
    }

    public void addSuccessor(final String successor) {
        successors.add(successor);
    }

    public Set<String> getPredecessors() {
        return predecessors;
    }

    public Set<String> getSuccessors() {
        return successors;
    }

    public boolean isTargetComplete() {
        return state == TargetState.COMPLETE;
    }

    public boolean isTargetWaiting() {
        return state == TargetState.WAITING;
    }

    public void setState(final TargetState state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return String.format("%s [%s]", target.getName(), state);
    }

    @Override
    public void run() {
        executionNotifier.notifyStarting(this);
        try {
            //            if (target.getProject().isKeepGoingMode()) {
            targetExecutor.executeTarget(target);
            //            }
        } finally {
            executionNotifier.notifyComplete(this);
        }
    }
}
