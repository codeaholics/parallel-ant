package org.codeaholics.tools.build.pant;

import org.apache.tools.ant.Target;

public interface DependencyGraphEntryFactory {
    public DependencyGraphEntry create(Target target);
}
