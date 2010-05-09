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
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tools.ant.Target;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
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
    public void testReturnsSameDependencyGraphEntryWhenFindingDependenciesRepeatedlyForSameTarget() {
        final Target target = createAndAddTarget(TARGET_NAME1);
        final DependencyGraphEntry dependencyGraphEntry = createDependencyGraphEntry(target);

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
        final Target target1 = createAndAddTarget(TARGET_NAME1);
        final Target target2 = createAndAddTarget(TARGET_NAME2, TARGET_NAME1);
        final Target target3 = createAndAddTarget(TARGET_NAME3, TARGET_NAME1);
        final Target target4 = createAndAddTarget(TARGET_NAME4, TARGET_NAME2, TARGET_NAME3);

        final DependencyGraph dependencyGraph = new DependencyGraph(targetMap, dependencyGraphEntryFactory);

        final DependencyGraphEntry dependencyGraphEntryForTarget1 = expectCreateDependencyGraphEntry(target1);
        expectCreateDependencyGraphEntry(target2);
        expectCreateDependencyGraphEntry(target3);
        expectCreateDependencyGraphEntry(target4);

        dependencyGraph.buildDependencies(target4);

        assertThat(dependencyGraphEntryForTarget1.getSuccessors(), equalTo(setOf(TARGET_NAME3, TARGET_NAME2)));
    }

    @Test
    public void testCorrectlyDiscoversSchedulableTargets() {
        final Target target1 = createAndAddTarget(TARGET_NAME1);
        final Target target2 = createAndAddTarget(TARGET_NAME2, TARGET_NAME1);
        final Target target3 = createAndAddTarget(TARGET_NAME3, TARGET_NAME1);
        final Target target4 = createAndAddTarget(TARGET_NAME4, TARGET_NAME2, TARGET_NAME3);

        final DependencyGraph dependencyGraph = new DependencyGraph(targetMap, dependencyGraphEntryFactory);

        final DependencyGraphEntry dependencyGraphEntryForTarget1 = expectCreateDependencyGraphEntry(target1);
        final DependencyGraphEntry dependencyGraphEntryForTarget2 = expectCreateDependencyGraphEntry(target2);
        final DependencyGraphEntry dependencyGraphEntryForTarget3 = expectCreateDependencyGraphEntry(target3);
        final DependencyGraphEntry dependencyGraphEntryForTarget4 = expectCreateDependencyGraphEntry(target4);

        dependencyGraph.buildDependencies(target4);

        assertThat(dependencyGraph.discoverAllSchedulableTargets(), equalToUnsortedList(dependencyGraphEntryForTarget1));

        dependencyGraphEntryForTarget1.setState(TargetState.RUNNING);
        assertThat(dependencyGraph.discoverAllSchedulableTargets(), equalTo(Collections.<DependencyGraphEntry>emptyList()));

        dependencyGraphEntryForTarget1.setState(TargetState.COMPLETE);
        assertThat(dependencyGraph.discoverAllSchedulableTargets(), equalToUnsortedList(dependencyGraphEntryForTarget2,
                                                                                        dependencyGraphEntryForTarget3));

        dependencyGraphEntryForTarget2.setState(TargetState.QUEUED);
        dependencyGraphEntryForTarget3.setState(TargetState.COMPLETE);
        assertThat(dependencyGraph.discoverAllSchedulableTargets(), equalTo(Collections.<DependencyGraphEntry>emptyList()));

        dependencyGraphEntryForTarget2.setState(TargetState.COMPLETE);
        assertThat(dependencyGraph.discoverAllSchedulableTargets(), equalToUnsortedList(dependencyGraphEntryForTarget4));

        dependencyGraphEntryForTarget4.setState(TargetState.COMPLETE);
        assertThat(dependencyGraph.discoverAllSchedulableTargets(), equalTo(Collections.<DependencyGraphEntry>emptyList()));
    }

    private DependencyGraphEntry expectCreateDependencyGraphEntry(final Target target) {
        final DependencyGraphEntry dependencyGraphEntry = createDependencyGraphEntry(target);

        mockery.checking(new Expectations() {{
            one(dependencyGraphEntryFactory).create(target);
            will(returnValue(dependencyGraphEntry));
        }});

        return dependencyGraphEntry;
    }

    private Target createAndAddTarget(final String targetName, final String... dependencies) {
        final Target target = AntTestHelper.createTarget(mockery, targetName, dependencies);

        targetMap.put(targetName, target);

        return target;
    }

    private DependencyGraphEntry createDependencyGraphEntry(final Target target) {
        return new DependencyGraphEntry(target, null, null);
    }

    private <T> Set<T> setOf(final T... objects) {
        return new HashSet<T>(Arrays.asList(objects));
    }


    private Matcher<List<DependencyGraphEntry>> equalToUnsortedList(final DependencyGraphEntry... dependencyGraphEntries) {
        return new TypeSafeMatcher<List<DependencyGraphEntry>>() {
            @Override
            public void describeTo(final Description description) {
                description.appendValueList("the list ", ",", " (in any order)", dependencyGraphEntries);
            }

            @Override
            public boolean matchesSafely(final List<DependencyGraphEntry> list) {
                if (list.size() != dependencyGraphEntries.length) {
                    return false;
                }

                return list.containsAll(Arrays.asList(dependencyGraphEntries));
            }
        };
    }

}
