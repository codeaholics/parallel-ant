package org.codeaholics.tools.build.pant;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.tools.ant.Target;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class DependencyGraphTest {
    private static final String TARGET_NAME1 = "targetName1";
    private static final String TARGET_NAME2 = "targetName2";
    private static final String TARGET_NAME3 = "targetName3";
    private static final String TARGET_NAME4 = "targetName4";

    private Mockery mockery;
    private DependencyGraphEntryFactory dependencyGraphEntryFactory;
    private Map<String, Target> targetMap;

    @Before
    public void setUp() {
        mockery = new Mockery();
        mockery.setImposteriser(ClassImposteriser.INSTANCE);

        dependencyGraphEntryFactory = mockery.mock(DependencyGraphEntryFactory.class);
        targetMap = new HashMap<String, Target>();
    }

    @Test
    public void testReturnsSameDependencyGraphEntryWhenFindingDependenciesForSameTarget() {
        final Target target = createTargetAndPutInMap(TARGET_NAME1);
        final DependencyGraphEntry dependencyGraphEntry = new DependencyGraphEntry(target, null, null);

        mockery.checking(new Expectations() {{
            one(dependencyGraphEntryFactory).create(target);  // only calls the factory once
            will(returnValue(dependencyGraphEntry));
        }});

        final DependencyGraph dependencyGraph = new DependencyGraph(targetMap, dependencyGraphEntryFactory);

        // both the first and second call for the same target return the same graph entry
        assertThat(dependencyGraph.buildDependencies(target), sameInstance(dependencyGraphEntry));
        assertThat(dependencyGraph.buildDependencies(target), sameInstance(dependencyGraphEntry));
    }

    @Test
    public void testCorrectlyDeterminesSuccessors() {
        createTargetAndPutInMap(TARGET_NAME1);
        createTargetAndPutInMap(TARGET_NAME2, TARGET_NAME1);
        createTargetAndPutInMap(TARGET_NAME3, TARGET_NAME1);
        final Target target4 = createTargetAndPutInMap(TARGET_NAME4, TARGET_NAME2, TARGET_NAME3);

        final Set<String> expectedSuccessorsOfTarget1 = new HashSet<String>(Arrays.asList(TARGET_NAME3, TARGET_NAME2));

        final CapturingDependencyGraphEntryFactory capturingDependencyGraphEntryFactory =
            new CapturingDependencyGraphEntryFactory(null, null);

        final DependencyGraph dependencyGraph = new DependencyGraph(targetMap, capturingDependencyGraphEntryFactory);

        dependencyGraph.buildDependencies(target4);

        assertThat(capturingDependencyGraphEntryFactory.get(TARGET_NAME1).getSuccessors(),
                   equalTo(expectedSuccessorsOfTarget1));
    }

    private Target createTargetAndPutInMap(final String targetName, final String... dependencies) {
        final Target target = AntTestHelper.createTarget(mockery, targetName, dependencies);

        targetMap.put(targetName, target);

        return target;
    }

    private static final class CapturingDependencyGraphEntryFactory extends DependencyGraphEntryFactory {
        private final Map<String, DependencyGraphEntry> capturedEntries;

        public CapturingDependencyGraphEntryFactory(final TargetExecutionNotifier targetExecutionNotifier,
                                                    final TargetExecutor targetExecutor) {
            super(targetExecutionNotifier, targetExecutor);
            capturedEntries = new HashMap<String, DependencyGraphEntry>();
        }

        @Override
        public DependencyGraphEntry create(final Target target) {
            final DependencyGraphEntry graphEntry = super.create(target);
            capturedEntries.put(target.getName(), graphEntry);
            return graphEntry;
        }

        public DependencyGraphEntry get(final String targetName) {
            return capturedEntries.get(targetName);
        }
    }
}
