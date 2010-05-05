package org.codeaholics.tools.build.pant;

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
    private TargetExecutor targetExecutor;
    private Target target;
    private DependencyGraphEntry dependencyGraphEntry;

    @Before
    public void setUp() {
        mockery = new Mockery();

        target = new Target();
        targetExecutionNotifier = mockery.mock(TargetExecutionNotifier.class);
        targetExecutor = mockery.mock(TargetExecutor.class);

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