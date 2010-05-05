package org.codeaholics.tools.build.pant;

import org.apache.tools.ant.Target;

public class TargetExecutorImpl implements TargetExecutor {
    public void executeTarget(final Target target) {
        target.performTasks();
    }
}
