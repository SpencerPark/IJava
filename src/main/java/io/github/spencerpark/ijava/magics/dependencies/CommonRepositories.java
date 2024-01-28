/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 Spencer Park
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.github.spencerpark.ijava.magics.dependencies;

import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.IBiblioResolver;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Path;

public class CommonRepositories {
    protected static final String MAVEN_PATTERN_PREFIX = "[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier])";
    protected static final String MAVEN_ARTIFACT_PATTERN = MAVEN_PATTERN_PREFIX + ".[ext]";
    protected static final String MAVEN_POM_PATTERN = MAVEN_PATTERN_PREFIX + ".pom";

    public static DependencyResolver maven(String name, String urlRaw) {
        IBiblioResolver resolver = new IBiblioResolver();
        resolver.setM2compatible(true);
        resolver.setUseMavenMetadata(true);
        resolver.setUsepoms(true);

        resolver.setRoot(urlRaw);
        resolver.setName(name);

        return resolver;
    }

    public static DependencyResolver mavenCentral() {
        return CommonRepositories.maven("maven-central", "https://repo.maven.apache.org/maven2/");
    }

    public static DependencyResolver jcenter() {
        return CommonRepositories.maven("jcenter", "https://jcenter.bintray.com/");
    }

    public static DependencyResolver mavenLocal() {
        IBiblioResolver resolver = new IBiblioResolver();
        resolver.setM2compatible(true);
        resolver.setUseMavenMetadata(true);
        resolver.setUsepoms(true);

        resolver.setName("maven-local");

        Path localRepoPath;
        try {
            localRepoPath = Maven.getInstance().getConfiguredLocalRepositoryPath();
        } catch (IOException e) {
            throw new RuntimeException("Error reading maven settings. " + e.getLocalizedMessage(), e);
        } catch (SAXException e) {
            throw new RuntimeException("Error parsing maven settings. " + e.getLocalizedMessage(), e);
        }

        resolver.setRoot("file:///" + localRepoPath.toString());

        return resolver;
    }
}
