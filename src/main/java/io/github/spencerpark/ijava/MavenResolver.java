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
package io.github.spencerpark.ijava;

import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;
import io.github.spencerpark.jupyter.kernel.magic.registry.LineMagic;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.jboss.shrinkwrap.resolver.api.maven.ConfigurableMavenResolverSystem;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.PomEquippedResolveStage;
import org.jboss.shrinkwrap.resolver.api.maven.ScopeType;
import org.jboss.shrinkwrap.resolver.api.maven.repository.MavenRemoteRepositories;
import org.jboss.shrinkwrap.resolver.api.maven.repository.MavenRemoteRepository;
import org.jboss.shrinkwrap.resolver.api.maven.strategy.TransitiveStrategy;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MavenResolver {
    private final Consumer<String> addToClasspath;
    private final List<MavenRemoteRepository> repos;

    public MavenResolver(Consumer<String> addToClasspath) {
        this.addToClasspath = addToClasspath;
        this.repos = new LinkedList<>();
    }

    public void addRemoteRepo(String name, String url) {
        this.repos.add(MavenRemoteRepositories.createRemoteRepository(name, url, "default"));
    }

    public void addRemoteRepo(String name, URL url) {
        this.repos.add(MavenRemoteRepositories.createRemoteRepository(name, url, "default"));
    }

    public List<File> resolveMavenDependency(String canonical) {
        ConfigurableMavenResolverSystem resolver = Maven.configureResolver()
                .withClassPathResolution(true)
                .withMavenCentralRepo(true);

        this.repos.forEach(resolver::withRemoteRepo);

        return resolver.resolve(canonical)
                .using(TransitiveStrategy.INSTANCE)
                .asList(File.class);
    }

    private String solidifyPartialPOM(String rawIn) throws ParserConfigurationException, IOException, SAXException, TransformerException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        DocumentBuilder builder = factory.newDocumentBuilder();

        // Wrap in a dummy tag to allow fragments
        InputStream inStream = new SequenceInputStream(Collections.enumeration(Arrays.asList(
                new ByteArrayInputStream("<ijava>".getBytes(Charset.forName("utf-8"))),
                new ByteArrayInputStream(rawIn.getBytes(Charset.forName("utf-8"))),
                new ByteArrayInputStream("</ijava>".getBytes(Charset.forName("utf-8")))
        )));

        Document doc = builder.parse(inStream);
        NodeList rootChildren = doc.getDocumentElement().getChildNodes();

        // If input was a single "project" tag then we don't touch it. It is assumed
        // to be complete.
        if (rootChildren.getLength() == 1 && "project".equalsIgnoreCase(rootChildren.item(0).getNodeName()))
            return this.writeDOM(new DOMSource(rootChildren.item(0)));

        // Put the pieces together and fill in the blanks.
        Document fixed = builder.newDocument();

        Node project = fixed.appendChild(fixed.createElement("project"));

        Node dependencies = project.appendChild(fixed.createElement("dependencies"));
        Node repositories = project.appendChild(fixed.createElement("repositories"));

        boolean setModelVersion = false;
        boolean setGroupId = false;
        boolean setArtifactId = false;
        boolean setVersion = false;

        for (int i = 0; i < rootChildren.getLength(); i++) {
            Node child = rootChildren.item(i);

            switch (child.getNodeName()) {
                case "modelVersion":
                    setModelVersion = true;
                    this.appendChildInNewDoc(child, fixed, project);
                    break;
                case "groupId":
                    setGroupId = true;
                    this.appendChildInNewDoc(child, fixed, project);
                    break;
                case "artifactId":
                    setArtifactId = true;
                    this.appendChildInNewDoc(child, fixed, project);
                    break;
                case "version":
                    setVersion = true;
                    this.appendChildInNewDoc(child, fixed, project);
                    break;
                case "dependency":
                    this.appendChildInNewDoc(child, fixed, dependencies);
                    break;
                case "repository":
                    this.appendChildInNewDoc(child, fixed, repositories);
                    break;
                case "dependencies":
                    // Add all dependencies to the collecting tag
                    NodeList dependencyChildren = child.getChildNodes();
                    for (int j = 0; j < dependencyChildren.getLength(); j++)
                        this.appendChildInNewDoc(dependencyChildren.item(j), fixed, dependencies);
                    break;
                case "repositories":
                    // Add all repositories to the collecting tag
                    NodeList repositoryChildren = child.getChildNodes();
                    for (int j = 0; j < repositoryChildren.getLength(); j++)
                        this.appendChildInNewDoc(repositoryChildren.item(j), fixed, repositories);
                    break;
                default:
                    this.appendChildInNewDoc(child, fixed, project);
                    break;
            }
        }

        if (!setModelVersion) {
            Node modelVersion = project.appendChild(fixed.createElement("modelVersion"));
            modelVersion.setTextContent("4.0.0");
        }

        if (!setGroupId) {
            Node groupId = project.appendChild(fixed.createElement("groupId"));
            groupId.setTextContent("ijava.notebook");
        }

        if (!setArtifactId) {
            Node artifactId = project.appendChild(fixed.createElement("artifactId"));
            artifactId.setTextContent("cell");
        }

        if (!setVersion) {
            Node version = project.appendChild(fixed.createElement("version"));
            version.setTextContent("1");
        }

        return this.writeDOM(new DOMSource(fixed));
    }

    private void appendChildInNewDoc(Node oldNode, Document doc, Node newParent) {
        Node newNode = oldNode.cloneNode(true);
        doc.adoptNode(newNode);
        newParent.appendChild(newNode);
    }

    private String writeDOM(Source src) throws TransformerException, UnsupportedEncodingException {
        Transformer idTransformer = TransformerFactory.newInstance().newTransformer();
        idTransformer.setOutputProperty(OutputKeys.INDENT, "yes");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Result dest = new StreamResult(out);

        idTransformer.transform(src, dest);

        return out.toString("utf-8");
    }

    public void addJarsToClasspath(Iterable<String> jars) {
        jars.forEach(this.addToClasspath);
    }

    @LineMagic(aliases = { "addMavenDependency", "maven" })
    public void addMavenDependencies(List<String> args) {
        for (String arg : args) {
            this.addJarsToClasspath(
                    this.resolveMavenDependency(arg).stream()
                            .map(File::getAbsolutePath)
                            ::iterator
            );
        }
    }

    @LineMagic(aliases = { "mavenRepo" })
    public void addMavenRepo(List<String> args) {
        if (args.size() != 2)
            throw new IllegalArgumentException("Expected 2 arguments: repository id and url. Got: " + args);

        String id = args.get(0);
        String url = args.get(1);

        this.addRemoteRepo(id, url);
    }

    @CellMagic
    public void loadFromPOM(List<String> args, String body) throws Exception {
        try {
            Path tempPom = Files.createTempFile("ijava-maven-", ".pom");
            String rawPom = this.solidifyPartialPOM(body);
            Files.write(tempPom, rawPom.getBytes(Charset.forName("utf-8")));

            List<String> loadArgs = new ArrayList<>(args.size() + 1);
            loadArgs.add(tempPom.toAbsolutePath().toString());
            loadArgs.addAll(args);

            this.loadFromPOM(loadArgs);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @LineMagic
    public void loadFromPOM(List<String> args) {
        if (args.isEmpty())
            throw new IllegalArgumentException("Loading from POM requires at least the path to the POM file");

        String pomFile = args.get(0);
        List<String> scopes = args.subList(1, args.size());

        PomEquippedResolveStage stage = Maven.resolver().loadPomFromFile(pomFile);

        if (scopes.isEmpty())
            stage = stage.importCompileAndRuntimeDependencies();
        else
            stage = stage.importDependencies(scopes.stream().map(ScopeType::fromScopeType).toArray(ScopeType[]::new));

        this.addJarsToClasspath(
                stage.resolve()
                        .withTransitivity()
                        .asList(File.class).stream()
                        .map(File::getAbsolutePath)
                        ::iterator
        );
    }
}
