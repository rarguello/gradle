/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.model.internal

import org.gradle.plugins.ide.internal.IdeDependenciesExtractor
import org.gradle.plugins.ide.internal.IdeDependenciesExtractor.IdeLocalFileDependency
import org.gradle.plugins.ide.internal.IdeDependenciesExtractor.IdeProjectDependency
import org.gradle.plugins.ide.internal.IdeDependenciesExtractor.IdeRepoFileDependency
import org.gradle.plugins.ide.eclipse.model.*

/**
 * @author Hans Dockter
 */
class ClasspathFactory {

    private final ClasspathEntryBuilder outputCreator = new ClasspathEntryBuilder() {
        void update(List<ClasspathEntry> entries, EclipseClasspath eclipseClasspath) {
            entries.add(new Output(eclipseClasspath.project.relativePath(eclipseClasspath.defaultOutputDir)))
        }
    }

    private final ClasspathEntryBuilder containersCreator = new ClasspathEntryBuilder() {
        void update(List<ClasspathEntry> entries, EclipseClasspath eclipseClasspath) {
            eclipseClasspath.containers.each { container ->
                Container entry = new Container(container)
                entry.exported = true
                entries << entry
            }
        }
    }

    private final ClasspathEntryBuilder projectDependenciesCreator = new ClasspathEntryBuilder() {
        void update(List<ClasspathEntry> entries, EclipseClasspath eclipseClasspath) {
            entries.addAll(dependenciesExtractor.extractProjectDependencies(eclipseClasspath.plusConfigurations, eclipseClasspath.minusConfigurations)
                .collect { IdeProjectDependency it -> new ProjectDependencyBuilder().build(it.project, it.declaredConfiguration.name) })
        }
    }

    private final ClasspathEntryBuilder librariesCreator = new ClasspathEntryBuilder() {
        void update(List<ClasspathEntry> entries, EclipseClasspath classpath) {
            def referenceFactory = classpath.fileReferenceFactory

            dependenciesExtractor.extractRepoFileDependencies(
                    classpath.project.configurations, classpath.plusConfigurations, classpath.minusConfigurations, classpath.downloadSources, classpath.downloadJavadoc)
            .each { IdeRepoFileDependency it ->
                entries << createLibraryEntry(it.file, it.sourceFile, it.javadocFile, it.declaredConfiguration.name, referenceFactory)
            }

            dependenciesExtractor.extractLocalFileDependencies(classpath.plusConfigurations, classpath.minusConfigurations)
            .each { IdeLocalFileDependency it ->
                entries << createLibraryEntry(it.file, null, null, it.declaredConfiguration.name, referenceFactory)
            }
        }
    }

    private final sourceFoldersCreator = new SourceFoldersCreator()
    private final IdeDependenciesExtractor dependenciesExtractor = new IdeDependenciesExtractor()
    private final classFoldersCreator = new ClassFoldersCreator()

    List<ClasspathEntry> createEntries(EclipseClasspath classpath) {
        def entries = []
        outputCreator.update(entries, classpath)
        sourceFoldersCreator.populateForClasspath(entries, classpath)
        containersCreator.update(entries, classpath)
        if (classpath.projectDependenciesOnly) {
            projectDependenciesCreator.update(entries, classpath)
        } else {
            projectDependenciesCreator.update(entries, classpath)
            librariesCreator.update(entries, classpath)
            entries.addAll(classFoldersCreator.create(classpath))
        }
        return entries
    }

    private AbstractLibrary createLibraryEntry(File binary, File source, File javadoc, String declaredConfigurationName, FileReferenceFactory referenceFactory) {
        def binaryRef = referenceFactory.fromFile(binary)
        def sourceRef = referenceFactory.fromFile(source)
        def javadocRef = referenceFactory.fromFile(javadoc)
        def out
        if (binaryRef.relativeToPathVariable) {
            out = new Variable(binaryRef)
        } else {
            out = new Library(binaryRef)
        }
        out.sourcePath = sourceRef
        out.javadocPath = javadocRef
        out.exported = true
        out.declaredConfigurationName = declaredConfigurationName
        out
    }
}

interface ClasspathEntryBuilder {
	void update(List<ClasspathEntry> entries, EclipseClasspath eclipseClasspath)
}
