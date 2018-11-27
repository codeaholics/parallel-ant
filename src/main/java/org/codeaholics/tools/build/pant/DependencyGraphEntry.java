package org.codeaholics.tools.build.pant;

/*
 *   Copyright 2010-2011 Danny Yates
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

import java.util.HashSet;
import java.util.Set;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Target;

public class DependencyGraphEntry implements Runnable {
    private final Target target;
    private final boolean isPrePhase;
    private final TargetExecutionNotifier executionNotifier;
    private final AntWrapper targetExecutor;

    private final Set<String> predecessors = new HashSet<String>();
    private final Set<String> successors = new HashSet<String>();

    private TargetState state = TargetState.WAITING;

    public DependencyGraphEntry(final Target target, final boolean isPrePhase,
                                final TargetExecutionNotifier executionNotifier,
                                final AntWrapper targetExecutor) {
        this.target = target;
        this.isPrePhase = isPrePhase;
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

    public boolean isPrePhase() {
        return isPrePhase;
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
            targetExecutor.executeTarget(target);
        } catch (BuildException e) {
            executionNotifier.notifyException(this, e);
        } finally {
            executionNotifier.notifyComplete(this);
        }
    }
}
