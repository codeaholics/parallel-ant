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
import org.jmock.Sequence;
import org.jmock.integration.junit4.JMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class DependencyGraphEntryTest {
    private Mockery mockery;
    private TargetExecutionNotifier targetExecutionNotifier;
    private AntWrapper targetExecutor;
    private Target target;
    private DependencyGraphEntry dependencyGraphEntry;

    @Before
    public void setUp() {
        mockery = new Mockery();

        target = new Target();
        targetExecutionNotifier = mockery.mock(TargetExecutionNotifier.class);
        targetExecutor = mockery.mock(AntWrapper.class);

        dependencyGraphEntry = new DependencyGraphEntry(target, targetExecutionNotifier, targetExecutor);
    }

    @Test
    public void testCallsNotifierAndRunsTargetInCorrectOrder() throws Exception {
        final Sequence sequence = mockery.sequence("in order");

        mockery.checking(new Expectations() {{
            one(targetExecutionNotifier).notifyStarting(dependencyGraphEntry);
            inSequence(sequence);

            one(targetExecutor).executeTarget(target);
            inSequence(sequence);

            one(targetExecutionNotifier).notifyComplete(dependencyGraphEntry);
            inSequence(sequence);
        }});

        dependencyGraphEntry.run();
    }

    @Test(expected = ExpectedRuntimeException.class)
    public void testCallsNotifyCompleteEvenIfExceptionIsThrown() throws Exception {
        mockery.checking(new Expectations() {{
            ignoring(targetExecutionNotifier).notifyStarting(with(any(DependencyGraphEntry.class)));

            allowing(targetExecutor).executeTarget(target);
            will(throwException(new ExpectedRuntimeException()));

            one(targetExecutionNotifier).notifyComplete(dependencyGraphEntry);
        }});

        dependencyGraphEntry.run();
    }

    @SuppressWarnings("serial")
    public class ExpectedRuntimeException extends RuntimeException {}
}