package org.codeaholics.tools.build.pant;

import java.util.Map;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Executor;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.helper.SingleCheckExecutor;

public class ParallelExecutor implements Executor {
    private static final SingleCheckExecutor SUB_EXECUTOR = new SingleCheckExecutor();

    @SuppressWarnings("unchecked")
    @Override
    public void executeTargets(final Project project, final String[] targetNames) throws BuildException {
        final Map<String, Target> targetsByName = project.getTargets();

        for (final String targetName : targetNames) {
            executeTarget(targetsByName.get(targetName), targetsByName);
        }
    }

    private void executeTarget(final Target target, final Map<String, Target> targetsByName) {
        final DependencyTree dependencyTree = new DependencyTree(target, targetsByName);
        dependencyTree.dump();
    }

    @Override
    public Executor getSubProjectExecutor() {
        return SUB_EXECUTOR;
    }

}