package org.codeaholics.tools.build.pant;

import org.apache.tools.ant.Target;

// This is a horrible interface which is only here because I want to unit test a call to Target.performTask() which
// is final and can't be mocked by JMock
public interface TargetExecutor {
    void executeTarget(Target target);
}
