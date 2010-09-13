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

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Target;

public class PrivateTargetCannotHaveTasksException extends BuildException {
    private static final long serialVersionUID = 6084380693932625320L;

    public PrivateTargetCannotHaveTasksException(final Target target) {
        super("Private target cannot have tasks: " + target.getName(), target.getLocation());
    }
}
