package org.codeaholics.tools.build.pant;

import java.util.HashSet;
import java.util.Set;

import org.apache.tools.ant.Target;

public class TargetWrapper implements Runnable {
    private final Target target;
    private final TargetExecutionNotifier executionNotifier;
    private final Set<String> predecessors = new HashSet<String>();
    private final Set<String> successors = new HashSet<String>();

    private TargetState state = TargetState.WAITING;

    public TargetWrapper(final Target target, final TargetExecutionNotifier executionNotifier) {
        this.target = target;
        this.executionNotifier = executionNotifier;
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

    public TargetState getState() {
        return state;
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
            System.out.println("Gonna run " + target.getName());
            try {
                Thread.sleep(500);
            } catch (final InterruptedException e) {
                // do nothing
            }
        } finally {
            executionNotifier.notifyComplete(this);
        }
    }
}
