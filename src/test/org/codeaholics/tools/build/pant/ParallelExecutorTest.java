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

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import java.util.Hashtable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.Sequence;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JMock;
import org.jmock.lib.action.CustomAction;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import org.junit.internal.matchers.TypeSafeMatcher;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class ParallelExecutorTest {
    private static final String TARGET_NAME1 = "targetName1";
    private static final String TARGET_NAME2 = "targetName2";
    private static final String TARGET_NAME3 = "targetName3";

    private Mockery mockery;
    private ParallelExecutor parallelExecutor;
    private ExecutorServiceFactory executorServiceFactory;
    private Project project;
    private ExecutorService executorService;
    private Target target1WithNoDependencies;
    private Target target2WithNoDependencies;
    private Target target3DependingOnTargets1And2;

    @Before
    public void setUp() {
        mockery = new Mockery();
        mockery.setImposteriser(ClassImposteriser.INSTANCE);

        parallelExecutor = new ParallelExecutor();
        executorServiceFactory = mockery.mock(ExecutorServiceFactory.class);
        executorService = mockery.mock(ExecutorService.class);
        parallelExecutor.setExecutorServiceFactory(executorServiceFactory);
        project = mockery.mock(Project.class);

        target1WithNoDependencies = AntTestHelper.createTarget(mockery, TARGET_NAME1);
        target2WithNoDependencies = AntTestHelper.createTarget(mockery, TARGET_NAME2);
        target3DependingOnTargets1And2 = AntTestHelper.createTarget(mockery, TARGET_NAME3, TARGET_NAME1, TARGET_NAME2);
    }

    @Test(expected = BuildException.class)
    public void testThrowsExceptionAndStopsOnUnknownTargetWithoutKeepGoingFlag() throws InterruptedException {
        final Hashtable<String, Target> targets = new Hashtable<String, Target>();

        targets.put(TARGET_NAME1, target1WithNoDependencies);
        targets.put(TARGET_NAME2, target2WithNoDependencies);

        allowNormalInteractions(targets, false);

        mockery.checking(new Expectations() {{
            one(executorService).submit(with(dependencyGraphEntryReferencingTarget(target1WithNoDependencies)));
            will(throwException(new BuildException()));

            never(executorService).submit(with(dependencyGraphEntryReferencingTarget(target2WithNoDependencies)));
        }});

        parallelExecutor.executeTargets(project, new String[] {TARGET_NAME1, TARGET_NAME2});
    }

    @Test(expected = BuildException.class)
    public void testThrowsExceptionAfterCompletionOnUnknownTargetWithKeepGoingFlag() throws InterruptedException {
        final Hashtable<String, Target> targets = new Hashtable<String, Target>();

        targets.put(TARGET_NAME1, target1WithNoDependencies);
        targets.put(TARGET_NAME2, target2WithNoDependencies);

        allowNormalInteractions(targets, true);

        mockery.checking(new Expectations() {{
            one(executorService).submit(with(dependencyGraphEntryReferencingTarget(target1WithNoDependencies)));
            will(throwException(new BuildException()));

            one(executorService).submit(with(dependencyGraphEntryReferencingTarget(target2WithNoDependencies)));
        }});

        parallelExecutor.executeTargets(project, new String[] {TARGET_NAME1, TARGET_NAME2});
    }

    @Test
    public void testExecutesRequestedTargets() throws InterruptedException {
        final Hashtable<String, Target> targets = new Hashtable<String, Target>();

        targets.put(TARGET_NAME1, target1WithNoDependencies);
        targets.put(TARGET_NAME2, target2WithNoDependencies);

        allowNormalInteractions(targets, true);

        mockery.checking(new Expectations() {{
            one(executorService).submit(with(dependencyGraphEntryReferencingTarget(target1WithNoDependencies)));

            one(executorService).submit(with(dependencyGraphEntryReferencingTarget(target2WithNoDependencies)));
        }});

        parallelExecutor.executeTargets(project, new String[] {TARGET_NAME1, TARGET_NAME2});
    }

    @Test
    public void testExecutesRequestedTargetAfterAllDependencies() throws InterruptedException {
        final Hashtable<String, Target> targets = new Hashtable<String, Target>();

        targets.put(TARGET_NAME1, target1WithNoDependencies);
        targets.put(TARGET_NAME2, target2WithNoDependencies);
        targets.put(TARGET_NAME3, target3DependingOnTargets1And2);

        allowNormalInteractions(targets, true);

        // Used by the custom targetExecutor to record tasks as they run. Could be a Target[1] instead.
        final AtomicReference<Target> lastRunTarget = new AtomicReference<Target>(null);

        parallelExecutor.setTargetExecutor(new TargetExecutor() {
            @Override
            public void executeTarget(final Target target) {
                lastRunTarget.set(target);
            }
        });

        // We can't do this with a state, because, in theory, target1 and target2 could be run in any order.
        final Sequence sequence = mockery.sequence("executor shutdown after execution of target 3");

        mockery.checking(new Expectations() {{
            one(executorService).submit(with(dependencyGraphEntryReferencingTarget(target1WithNoDependencies)));
            will(runTarget());

            one(executorService).submit(with(dependencyGraphEntryReferencingTarget(target2WithNoDependencies)));
            will(runTarget());

            one(executorService).submit(with(dependencyGraphEntryReferencingTarget(target3DependingOnTargets1And2)));
            inSequence(sequence);
            will(runTarget());

            one(executorService).shutdown();
            inSequence(sequence);
        }});

        parallelExecutor.executeTargets(project, new String[] {TARGET_NAME3});

        // This test relies on the fact that targets are executed as soon as they are submitted (the custom action in
        // runTarget()).
        assertThat(lastRunTarget.get(), equalTo(target3DependingOnTargets1And2));
    }

    private void allowNormalInteractions(final Hashtable<String, Target> targets, final boolean keepGoingMode) throws InterruptedException {
        mockery.checking(new Expectations() {{
            allowing(project).getTargets();
            will(returnValue(targets));

            allowing(project).isKeepGoingMode();
            will(returnValue(keepGoingMode));

            allowing(executorServiceFactory).create(with(any(Integer.TYPE)));
            will(returnValue(executorService));

            allowing(executorService).awaitTermination(with(any(Long.TYPE)), with(any(TimeUnit.class)));
            will(returnValue(true));
        }});
    }

    private static Action runTarget() {
        return new CustomAction("run target") {
            @Override
            public Object invoke(final Invocation invocation) throws Throwable {
                final DependencyGraphEntry dependencyGraphEntry = (DependencyGraphEntry)invocation.getParameter(0);
                dependencyGraphEntry.run();
                return null;
            }
        };
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
