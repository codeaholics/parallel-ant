package org.codeaholics.tools.build.pant;

public interface TargetExecutionNotifier {
    void notifyStarting(TargetWrapper targetWrapper);
    void notifyComplete(TargetWrapper targetWrapper);
}
