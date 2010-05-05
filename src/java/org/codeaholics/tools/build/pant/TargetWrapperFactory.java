package org.codeaholics.tools.build.pant;

import org.apache.tools.ant.Target;

public class TargetWrapperFactory {
    private final TargetExecutionNotifier targetExecutionNotifier;

    public TargetWrapperFactory(final TargetExecutionNotifier targetExecutionNotifier) {
        this.targetExecutionNotifier = targetExecutionNotifier;
    }

    public TargetWrapper create(final Target target) {
        return new TargetWrapper(target, targetExecutionNotifier);
    }

}
