package org.codeaholics.tools.build.pant;

import org.apache.tools.ant.Target;
import org.jmock.Expectations;
import org.jmock.Mockery;

public class AntTestHelper {
    public static Target createTarget(final Mockery mockery, final String targetName, final String... dependencies) {
        final Target target = mockery.mock(Target.class, "target:" + targetName);

        mockery.checking(new Expectations() {{
            allowing(target).getName();
            will(returnValue(targetName));

            allowing(target).getDependencies();
            will(returnEnumeration(dependencies));
        }});

        return target;
    }

}
