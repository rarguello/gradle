/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.initialization;

import org.gradle.api.internal.GradleInternal;
import org.gradle.groovy.scripts.ScriptSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class CompositeInitScriptFinder implements InitScriptFinder {
    private final List<InitScriptFinder> finders;

    public CompositeInitScriptFinder(InitScriptFinder...finders) {
        this.finders = Arrays.asList(finders);
    }

    public void findScripts(GradleInternal gradle, Collection<ScriptSource> scripts) {
        for (InitScriptFinder finder : finders) {
            finder.findScripts(gradle, scripts);
        }
    }
}