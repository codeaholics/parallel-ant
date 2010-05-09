package org.codeaholics.tools.build.pant;

import org.apache.tools.ant.Target;

public class DependencyGraphEntryFactoryImpl implements DependencyGraphEntryFactory {
    private final TargetExecutionNotifier targetExecutionNotifier;
    private final TargetExecutor targetExecutor;

    public DependencyGraphEntryFactoryImpl(final TargetExecutionNotifier targetExecutionNotifier,
                                           final TargetExecutor targetExecutor) {
        this.targetExecutionNotifier = targetExecutionNotifier;
        this.targetExecutor = targetExecutor;
    }

    @Override
    public DependencyGraphEntry create(final Target target) {
        return new DependencyGraphEntry(target, targetExecutionNotifier, targetExecutor);
    }
}