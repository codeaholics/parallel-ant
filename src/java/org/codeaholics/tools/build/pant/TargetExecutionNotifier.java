package org.codeaholics.tools.build.pant;

public interface TargetExecutionNotifier {
    void notifyStarting(DependencyGraphEntry dependencyGraphEntry);

    void notifyComplete(DependencyGraphEntry dependencyGraphEntry);
}
