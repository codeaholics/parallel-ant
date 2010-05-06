package org.codeaholics.tools.build.pant;

import java.util.Hashtable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import org.junit.internal.matchers.TypeSafeMatcher;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class ParallelExecutorTest {
    private static final String TARGET_NAME1 = "targetName1";
    private static final String TARGET_NAME2 = "targetName2";

    private Mockery mockery;
    private ParallelExecutor parallelExecutor;
    private ExecutorServiceFactory executorServiceFactory;
    private Project project;
    private ExecutorService executorService;

    @Before
    public void setUp() {
        mockery = new Mockery();
        mockery.setImposteriser(ClassImposteriser.INSTANCE);

        parallelExecutor = new ParallelExecutor();
        executorServiceFactory = mockery.mock(ExecutorServiceFactory.class);
        executorService = mockery.mock(ExecutorService.class);
        parallelExecutor.setExecutorServiceFactory(executorServiceFactory);
        project = mockery.mock(Project.class);
    }

    @Test(expected = BuildException.class)
    public void testThrowsExceptionAndStopsOnUnknownTargetWithoutKeepGoingFlag() throws InterruptedException {
        final Target target1 = AntTestHelper.createTarget(mockery, TARGET_NAME1);
        final Target target2 = AntTestHelper.createTarget(mockery, TARGET_NAME2);

        final Hashtable<String, Target> targets = new Hashtable<String, Target>();

        targets.put(TARGET_NAME1, target1);
        targets.put(TARGET_NAME2, target2);

        mockery.checking(new Expectations() {{
            allowing(project).getTargets();
            will(returnValue(targets));

            allowing(project).isKeepGoingMode();
            will(returnValue(false));

            allowing(executorServiceFactory).create(with(any(Integer.TYPE)));
            will(returnValue(executorService));

            one(executorService).submit(with(dependencyGraphEntryReferencingTarget(target1)));
            will(throwException(new BuildException()));

            never(executorService).submit(with(dependencyGraphEntryReferencingTarget(target2)));

            allowing(executorService).awaitTermination(with(any(Long.TYPE)), with(any(TimeUnit.class)));
            will(returnValue(true));
        }});

        parallelExecutor.executeTargets(project, new String[] {TARGET_NAME1, TARGET_NAME2});
    }

    @Test(expected = BuildException.class)
    public void testThrowsExceptionAfterCompletionOnUnknownTargetWithKeepGoingFlag() throws InterruptedException {
        final Target target1 = AntTestHelper.createTarget(mockery, TARGET_NAME1);
        final Target target2 = AntTestHelper.createTarget(mockery, TARGET_NAME2);

        final Hashtable<String, Target> targets = new Hashtable<String, Target>();

        targets.put(TARGET_NAME1, target1);
        targets.put(TARGET_NAME2, target2);

        mockery.checking(new Expectations() {{
            allowing(project).getTargets();
            will(returnValue(targets));

            allowing(project).isKeepGoingMode();
            will(returnValue(true));

            allowing(executorServiceFactory).create(with(any(Integer.TYPE)));
            will(returnValue(executorService));

            one(executorService).submit(with(dependencyGraphEntryReferencingTarget(target1)));
            will(throwException(new BuildException()));

            one(executorService).submit(with(dependencyGraphEntryReferencingTarget(target2)));

            allowing(executorService).awaitTermination(with(any(Long.TYPE)), with(any(TimeUnit.class)));
            will(returnValue(true));
        }});

        parallelExecutor.executeTargets(project, new String[] {TARGET_NAME1, TARGET_NAME2});
    }

    private static Matcher<DependencyGraphEntry> dependencyGraphEntryReferencingTarget(final Target target) {
        return new TypeSafeMatcher<DependencyGraphEntry>() {
            @Override
            public void describeTo(final Description description) {
                description.appendText("a dependency graph entry referencing target '" + target.getName() + "'");
            }

            @Override
            public boolean matchesSafely(final DependencyGraphEntry dependencyGraphEntry) {
                return dependencyGraphEntry.getTarget() == target;
            }
        };
    }
}
