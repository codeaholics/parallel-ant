package org.codeaholics.tools.build.pant;

/*
 *   Copyright 2010 Danny Yates
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

            allowing(target).getLocation();
        }});

        return target;
    }

}
