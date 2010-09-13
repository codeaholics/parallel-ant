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

import static org.junit.Assert.fail;

import java.util.Hashtable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.Task;
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
    private static final String SPARE_TARGET_NAME = "spareTarget";
    private static final String UNKNOWN_PANT_TARGET_NAME = "pant:unknown";
    private static final String PANT_PRE_PHASE_TARGET_NAME = "pant:pre-phase";

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

    @Test(expected = ExpectedBuildException.class)
    public void testThrowsExceptionAndStopsOnUnknownTargetWithoutKeepGoingFlag() throws InterruptedException {
        final Hashtable<String, Target> targets = new Hashtable<String, Target>();

        targets.put(TARGET_NAME1, target1WithNoDependencies);
        targets.put(TARGET_NAME2, target2WithNoDependencies);

        allowNormalInteractions(targets, false);

        parallelExecutor.setAntWrapper(new ExceptionThrowingAntWrapper(target1WithNoDependencies));

        mockery.checking(new Expectations() {{
            one(executorService).submit(with(dependencyGraphEntryReferencingTarget(target1WithNoDependencies)));
            will(runTarget());

            one(executorService).shutdown();

            never(executorService).submit(with(dependencyGraphEntryReferencingTarget(target2WithNoDependencies)));
        }});

        parallelExecutor.executeTargets(project, new String[] {TARGET_NAME1, TARGET_NAME2});
    }

    @Test(expected = ExpectedBuildException.class)
    public void testThrowsExceptionAfterCompletionOnUnknownTargetWithKeepGoingFlag() throws InterruptedException {
        final Hashtable<String, Target> targets = new Hashtable<String, Target>();

        targets.put(TARGET_NAME1, target1WithNoDependencies);
        targets.put(TARGET_NAME2, target2WithNoDependencies);

        allowNormalInteractions(targets, true);

        parallelExecutor.setAntWrapper(new ExceptionThrowingAntWrapper(target1WithNoDependencies));

        final Sequence sequence = mockery.sequence("targets in specified order; shutdown executor between runs");

        mockery.checking(new Expectations() {{
            one(executorService).submit(with(dependencyGraphEntryReferencingTarget(target1WithNoDependencies)));
            inSequence(sequence);
            will(runTarget());

            one(executorService).shutdown();
            inSequence(sequence);

            one(executorService).submit(with(dependencyGraphEntryReferencingTarget(target2WithNoDependencies)));
            inSequence(sequence);
            will(runTarget());

            one(executorService).shutdown();
            inSequence(sequence);
        }});

        parallelExecutor.executeTargets(project, new String[] {TARGET_NAME1, TARGET_NAME2});
    }

    @Test
    public void testExecutesRequestedTargets() throws InterruptedException {
        final Hashtable<String, Target> targets = new Hashtable<String, Target>();

        targets.put(TARGET_NAME1, target1WithNoDependencies);
        targets.put(TARGET_NAME2, target2WithNoDependencies);

        allowNormalInteractions(targets, true);

        parallelExecutor.setAntWrapper(new NoOpAntWrapper());

        final Sequence sequence = mockery.sequence("targets in specified order; shutdown executor between runs");

        mockery.checking(new Expectations() {{
            one(executorService).submit(with(dependencyGraphEntryReferencingTarget(target1WithNoDependencies)));
            inSequence(sequence);
            will(runTarget());

            one(executorService).shutdown();
            inSequence(sequence);

            one(executorService).submit(with(dependencyGraphEntryReferencingTarget(target2WithNoDependencies)));
            inSequence(sequence);
            will(runTarget());

            one(executorService).shutdown();
            inSequence(sequence);
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

        parallelExecutor.setAntWrapper(new NoOpAntWrapper());

        final Sequence t1 = mockery.sequence("target 1 then target 3 then shutdown");
        final Sequence t2 = mockery.sequence("target 2 then target 3 then shutdown");

        mockery.checking(new Expectations() {{
            one(executorService).submit(with(dependencyGraphEntryReferencingTarget(target1WithNoDependencies)));
            inSequence(t1);
            will(runTarget());

            one(executorService).submit(with(dependencyGraphEntryReferencingTarget(target2WithNoDependencies)));
            inSequence(t2);
            will(runTarget());

            one(executorService).submit(with(dependencyGraphEntryReferencingTarget(target3DependingOnTargets1And2)));
            inSequences(t1, t2);
            will(runTarget());

            one(executorService).shutdown();
            inSequences(t1, t2);
        }});

        parallelExecutor.executeTargets(project, new String[] {TARGET_NAME3});
    }

    @Test(expected = ExpectedBuildException.class)
    public void testChecksForCyclesAndPropogatesBuildExceptions() {
        final String[] targets = {"target1", "target2", "target3"};

        final AntWrapper antWrapper = mockery.mock(AntWrapper.class);

        mockery.checking(new Expectations() {{
            atLeast(1).of(antWrapper).topologicalSortProject(with(same(project)), with(targets), with(true));
            will(throwException(new ExpectedBuildException()));

            atLeast(1).of(project).getTargets(); // wrapper should fetch targets

            never(antWrapper).executeTarget(with(any(Target.class)));
        }});

        parallelExecutor.setAntWrapper(antWrapper);
        parallelExecutor.executeTargets(project, targets);
    }

    @Test(expected = UnknownPrivateTargetException.class)
    public void testThrowsExceptionOnUnknownPrivateTarget() throws Exception {
        final Hashtable<String, Target> targets = new Hashtable<String, Target>();

        targets.put(TARGET_NAME1, target1WithNoDependencies);
        targets.put(UNKNOWN_PANT_TARGET_NAME, AntTestHelper.createTarget(mockery, UNKNOWN_PANT_TARGET_NAME));

        allowNormalInteractions(targets, false);

        final AntWrapper antWrapper = mockery.mock(AntWrapper.class);

        parallelExecutor.setAntWrapper(antWrapper);

        mockery.checking(new Expectations() {{
            never(executorService).submit(with(any(Runnable.class)));

            never(antWrapper).executeTarget(with(any(Target.class)));

            ignoring(antWrapper);
        }});

        parallelExecutor.executeTargets(project, new String[] {TARGET_NAME1});
    }

    @Test(expected = CannotDependOnPrivateTargetException.class)
    public void testThrowsExceptionIfTargetDependsOnPrivateTarget() throws Exception {
        final Hashtable<String, Target> targets = new Hashtable<String, Target>();

        final Target prePhaseTarget = AntTestHelper.createTarget(mockery, PANT_PRE_PHASE_TARGET_NAME);
        targets.put(PANT_PRE_PHASE_TARGET_NAME, prePhaseTarget);
        targets.put(SPARE_TARGET_NAME, AntTestHelper.createTarget(mockery, SPARE_TARGET_NAME, PANT_PRE_PHASE_TARGET_NAME));

        allowNormalInteractions(targets, false);

        parallelExecutor.setAntWrapper(new FailOnExecuteAntWrapper());

        mockery.checking(new Expectations() {{
            never(executorService).submit(with(any(Runnable.class)));

            allowing(prePhaseTarget).getTasks();
            will(returnValue(null));
        }});

        parallelExecutor.executeTargets(project, new String[] {UNKNOWN_PANT_TARGET_NAME});
    }

    @Test(expected = CannotExecutePrivateTargetException.class)
    public void testThrowsExceptionIfAskedToExecuteAPrivateTarget() throws Exception {
        final Hashtable<String, Target> targets = new Hashtable<String, Target>();

        final Target prePhaseTarget = AntTestHelper.createTarget(mockery, PANT_PRE_PHASE_TARGET_NAME, TARGET_NAME1);
        targets.put(TARGET_NAME1, target1WithNoDependencies);
        targets.put(PANT_PRE_PHASE_TARGET_NAME, prePhaseTarget);

        allowNormalInteractions(targets, false);

        parallelExecutor.setAntWrapper(new FailOnExecuteAntWrapper());

        mockery.checking(new Expectations() {{
            never(executorService).submit(with(any(Runnable.class)));

            allowing(prePhaseTarget).getTasks();
            will(returnValue(null));
        }});

        parallelExecutor.executeTargets(project, new String[] {PANT_PRE_PHASE_TARGET_NAME});
    }

    @Test(expected = PrivateTargetCannotHaveTasksException.class)
    public void testPrePhaseConfigurationTargetIsNotAllowedToHaveAnyTasks() throws Exception {
        final Hashtable<String, Target> targets = new Hashtable<String, Target>();

        final Target prePhaseTarget = AntTestHelper.createTarget(mockery, PANT_PRE_PHASE_TARGET_NAME);
        targets.put(TARGET_NAME1, target1WithNoDependencies);
        targets.put(PANT_PRE_PHASE_TARGET_NAME, prePhaseTarget);

        allowNormalInteractions(targets, false);

        parallelExecutor.setAntWrapper(new FailOnExecuteAntWrapper());

        mockery.checking(new Expectations() {{
            allowing(prePhaseTarget).getTasks();
            will(returnValue(new Task[] {null}));

            never(executorService).submit(with(any(Runnable.class)));
        }});

        parallelExecutor.executeTargets(project, new String[] {TARGET_NAME1});
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

    private abstract static class TopologicalSortSkippingAntWrapper implements AntWrapper {
        @Override
        public void topologicalSortProject(final Project project, final String[] roots, final boolean returnAll) {
            // do nothing
        }
    }

    private static final class ExceptionThrowingAntWrapper extends TopologicalSortSkippingAntWrapper {
        private final Target exceptionTarget;

        public ExceptionThrowingAntWrapper(final Target exceptionTarget) {
            this.exceptionTarget = exceptionTarget;
        }

        @Override
        public void executeTarget(final Target target) {
            if (target == exceptionTarget) {
                throw new ExpectedBuildException();
            }
        }
    }

    private static final class FailOnExecuteAntWrapper extends TopologicalSortSkippingAntWrapper {
        @Override
        public void executeTarget(final Target target) {
            fail("No targets should be executed");
        }
    }

    private static final class NoOpAntWrapper extends TopologicalSortSkippingAntWrapper {
        @Override
        public void executeTarget(final Target target) {
            // do nothing;
        }
    }

    private static final class ExpectedBuildException extends BuildException {
        private static final long serialVersionUID = 8043165801092558640L;
    }
}
