/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 ${author}
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
package io.github.spencerpark.ijava.magics;

import io.github.spencerpark.ijava.utils.ResolveDependency;
import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;
import io.github.spencerpark.jupyter.kernel.magic.registry.LineMagic;
import io.github.spencerpark.jupyter.kernel.magic.registry.MagicsArgs;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyResolutionException;

import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenResolver {
    private static final String DEFAULT_REPO_LOCAL = String.format("%s/.m2/repository", System.getProperty("user.home"));
    private static final String DEFAULT_REPO_TYPE = "default";

    private final Consumer<String> addToClasspath;
    final Pattern reposPattern = Pattern.compile("(?s).*?(?<repos><repository>.*</repository>).*");
    final Pattern depsPattern = Pattern.compile("(?s).*?(?<deps><dependency>.*</dependency>).*");
    final String pomSimpleTemplate = "<project><repositories>%s</repositories><dependencies>%s</dependencies></project>";
    private final List<RemoteRepository> remoteRepos = new ArrayList<>();

    public MavenResolver(Consumer<String> addToClasspath) {
        this.addToClasspath = addToClasspath;
        // central
        this.addRemoteRepo("central", "http://central.maven.org/maven2/");
    }

    private void addRemoteRepo(String id, String url) {
        this.remoteRepos.add(new RemoteRepository.Builder(id, DEFAULT_REPO_TYPE, url).build());
    }

    public void addJarsToClasspath(Iterable<String> jars) {
        jars.forEach(this.addToClasspath);
    }

    @LineMagic(aliases = {"addMavenDependency", "maven"})
    public void addMavenDependencies(List<String> args) {
        try {
            this.addJarsToClasspath(ResolveDependency.resolve(args, null, DEFAULT_REPO_LOCAL, remoteRepos));
        } catch (DependencyResolutionException | NoLocalRepositoryManagerException e) {
            throw new RuntimeException(e);
        }
    }

    @LineMagic(aliases = {"mavenRepo"})
    public void addMavenRepo(List<String> args) {
        MagicsArgs schema = MagicsArgs.builder().required("id").required("url").build();
        Map<String, List<String>> argData = schema.parse(args);

        String id = argData.get("id").get(0);
        String url = argData.get("url").get(0);
        this.addRemoteRepo(id, url);
    }

    @CellMagic(aliases = {"pom"})
    public void loadFromPOM(List<String> args, String body) throws Exception {
        try {
            Matcher reposMatcher = reposPattern.matcher(body);
            String repos = reposMatcher.find() ? reposMatcher.group("repos") : "";
            Matcher depsMatcher = depsPattern.matcher(body);
            String deps = depsMatcher.find() ? depsMatcher.group("deps") : "";
            String pomContent = String.format(pomSimpleTemplate, repos, deps);
            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model model = reader.read(new StringReader(pomContent));
            resolveModel(model);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @LineMagic(aliases = {"pom"})
    public void loadFromPOM(List<String> args) {
        if (args.isEmpty())
            throw new IllegalArgumentException("Loading from POM requires at least the path to the POM file");

        MagicsArgs schema = MagicsArgs.builder()
                .required("pomPath")
                .onlyKnownKeywords().onlyKnownFlags().build();

        Map<String, List<String>> argMap = schema.parse(args);

        String pomPath = argMap.get("pomPath").get(0);
        try {
            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model model = reader.read(new FileReader(pomPath, StandardCharsets.UTF_8));
            resolveModel(model);
        } catch (IOException | XmlPullParserException | DependencyResolutionException |
                 NoLocalRepositoryManagerException e) {
            throw new RuntimeException(e);
        }
    }

    private void resolveModel(Model model) throws DependencyResolutionException, NoLocalRepositoryManagerException {
        // add repo
        model.getRepositories().forEach(repo -> addRemoteRepo(repo.getId(), repo.getUrl()));
        // resolve dep
        List<String> coords = model.getDependencies()
                .stream()
                .map(dep -> String.format("%s:%s:%s", dep.getGroupId(), dep.getArtifactId(), dep.getVersion()))
                .toList();
        this.addJarsToClasspath(ResolveDependency.resolve(coords, null, DEFAULT_REPO_LOCAL, remoteRepos));
    }
}
