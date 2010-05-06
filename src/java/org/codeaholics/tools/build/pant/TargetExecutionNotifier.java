package org.codeaholics.tools.build.pant;

public interface TargetExecutionNotifier {
    public void notifyStarting(DependencyGraphEntry dependencyGraphEntry);
    public void notifyComplete(DependencyGraphEntry dependencyGraphEntry);
}
