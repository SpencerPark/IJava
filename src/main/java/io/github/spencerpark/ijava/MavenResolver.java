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

import io.github.spencerpark.ijava.magics.dependencies.CommonRepositories;
import io.github.spencerpark.ijava.magics.dependencies.Maven;
import io.github.spencerpark.ijava.magics.dependencies.MavenToIvy;
import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;
import io.github.spencerpark.jupyter.kernel.magic.registry.LineMagic;
import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.parser.m2.PomModuleDescriptorParser;
import org.apache.ivy.plugins.repository.url.URLResource;
import org.apache.ivy.plugins.resolver.ChainResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.util.DefaultMessageLogger;
import org.apache.ivy.util.Message;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuildingException;
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
import java.text.ParseException;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MavenResolver {
    private static final String DEFAULT_RESOLVER_NAME = "default";

    /**
     * "master" includes the artifact published by the module.
     * "runtime" includes the dependencies required for the module to run and
     * extends "compile" which is the dependencies required to compile the module.
     */
    private static final String[] DEFAULT_RESOLVE_CONFS = { "master", "runtime" };

    /**
     * The ivy artifact type corresponding to a binary artifact for a module.
     */
    private static final String JAR_TYPE = "jar";

    /**
     * The ivy artifact type corresponding to a source code artifact for a module. This
     * is still usually a ".jar" file but that corresponds to the "ext" not the "type".
     */
    private static final String SOURCE_TYPE = "source";

    /**
     * The ivy artifact type corresponding to a javadoc (HTML) artifact for a module. This
     *      * is still usually a ".jar" file but that corresponds to the "ext" not the "type".
     */
    private static final String JAVADOC_TYPE = "javadoc";

    private static final Pattern IVY_MRID_PATTERN = Pattern.compile(
            "^(?<organization>[-\\w/._+=]*)#(?<name>[-\\w/._+=]+)(?:#(?<branch>[-\\w/._+=]+))?;(?<revision>[-\\w/._+=,\\[\\]{}():@]+)$"
    );
    private static final Pattern MAVEN_MRID_PATTERN = Pattern.compile(
            "^(?<group>[^:\\s]+):(?<artifact>[^:\\s]+)(?::(?<packaging>[^:\\s]*)(?::(?<classifier>[^:\\s]+))?)?:(?<version>[^:\\s]+)$"
    );

    private final Consumer<String> addToClasspath;
    private final List<DependencyResolver> repos;

    public MavenResolver(Consumer<String> addToClasspath) {
        this.addToClasspath = addToClasspath;
        this.repos = new LinkedList<>();
        this.repos.add(CommonRepositories.mavenCentral());
    }

    public void addRemoteRepo(String name, String url) {
        if (DEFAULT_RESOLVER_NAME.equals(name))
            throw new IllegalArgumentException("Illegal repository name, cannot use '" + DEFAULT_RESOLVER_NAME + "'.");

        this.repos.add(CommonRepositories.maven(name, url));
    }

    private DependencyResolver searchAllReposResolver() {
        ChainResolver resolver = new ChainResolver();
        resolver.setName(DEFAULT_RESOLVER_NAME);

        this.repos.forEach(resolver::add);

        return resolver;
    }

    private static ModuleRevisionId parseCanonicalArtifactName(String canonical) {
        Matcher m = IVY_MRID_PATTERN.matcher(canonical);
        if (m.matches()) {
            System.out.println(m.toString());
            return ModuleRevisionId.newInstance(
                    m.group("organization"),
                    m.group("name"),
                    m.group("branch"),
                    m.group("revision")
            );
        }

        m = MAVEN_MRID_PATTERN.matcher(canonical);
        if (m.matches()) {
            System.out.printf("packaging=%s, classifier=%s, group=%s, artifact=%s, version=%s%n",
                    m.group("packaging"), m.group("classifier"), m.group("group"), m.group("artifact"), m.group("version"));

            String packaging = m.group("packaging");
            String classifier = m.group("classifier");

            return ModuleRevisionId.newInstance(
                    m.group("group"),
                    m.group("artifact"),
                    m.group("version"),
                    packaging == null
                            ? Collections.emptyMap()
                            : classifier == null
                                    ? Map.of("ext", packaging)
                                    : Map.of("ext", packaging, "m:classifier", classifier)
            );
        }

        throw new IllegalArgumentException("Cannot resolve '" + canonical + "' as maven or ivy coordinates.");
    }

    private Ivy createDefaultIvyInstance() {
        Ivy ivy = new Ivy();

        ivy.getLoggerEngine().setDefaultLogger(new DefaultMessageLogger(Message.MSG_VERBOSE));
        ivy.setSettings(new IvySettings());

        ivy.bind();

        return ivy;
    }

    public List<File> resolveMavenDependency(String canonical) throws IOException, ParseException {
        DependencyResolver rootResolver = this.searchAllReposResolver();

        Ivy ivy = this.createDefaultIvyInstance();
        IvySettings settings = ivy.getSettings();

        settings.addResolver(rootResolver);
        settings.setDefaultResolver(rootResolver.getName());

        ResolveOptions resolveOptions = new ResolveOptions();
        resolveOptions.setTransitive(true);
        resolveOptions.setDownload(true);

        ModuleRevisionId artifactIdentifier = MavenResolver.parseCanonicalArtifactName(canonical);
        DefaultModuleDescriptor containerModule = DefaultModuleDescriptor.newCallerInstance(
                artifactIdentifier,
                DEFAULT_RESOLVE_CONFS,
                true, // Transitive
                false // Changing - the resolver will set this based on SNAPSHOT since they are all m2 compatible
        );

        ResolveReport resolved = ivy.resolve(containerModule, resolveOptions);
        if (resolved.hasError())
            // TODO better error...
            throw new RuntimeException("Error resolving '" + canonical + "'. " + resolved.getAllProblemMessages());

        return Arrays.stream(resolved.getAllArtifactsReports())
                .filter(a -> "jar".equalsIgnoreCase(a.getType()))
                .map(ArtifactDownloadReport::getLocalFile)
                .collect(Collectors.toList());
    }

    private File convertPomToIvy(File pomFile) throws IOException, ParseException {
        PomModuleDescriptorParser parser = PomModuleDescriptorParser.getInstance();

        URL pomUrl = pomFile.toURI().toURL();

        ModuleDescriptor pomModule = parser.parseDescriptor(new IvySettings(), pomFile.toURI().toURL(), false);

        File tempIvyFile = File.createTempFile("ijava-ivy-", ".xml").getAbsoluteFile();
        tempIvyFile.deleteOnExit();

        parser.toIvyFile(pomUrl.openStream(), new URLResource(pomUrl), tempIvyFile, pomModule);

        // TODO print to the ivy messaging engine
        Files.readAllLines(tempIvyFile.toPath(), Charset.forName("utf8"))
                .forEach(System.out::println);

        return tempIvyFile;
    }

    private void addPomReposToIvySettings(IvySettings settings, File pomFile) throws ModelBuildingException {
        Model mavenModel = Maven.getInstance().readEffectiveModel(pomFile).getEffectiveModel();
        ChainResolver pomRepos = MavenToIvy.createChainForModelRepositories(mavenModel);
        pomRepos.setName(DEFAULT_RESOLVER_NAME);

        settings.addResolver(pomRepos);
        settings.setDefaultResolver(DEFAULT_RESOLVER_NAME);
    }

    private List<File> resolveFromIvyFile(Ivy ivy, File ivyFile, List<String> scopes) throws IOException, ParseException {
        ResolveOptions resolveOptions = new ResolveOptions();
        resolveOptions.setTransitive(true);
        resolveOptions.setDownload(true);
        resolveOptions.setConfs(!scopes.isEmpty()
                ? scopes.toArray(new String[0])
                : DEFAULT_RESOLVE_CONFS
        );

        ResolveReport resolved = ivy.resolve(ivyFile, resolveOptions);
        if (resolved.hasError())
            // TODO better error...
            throw new RuntimeException("Error resolving '" + ivyFile + "'. " + resolved.getAllProblemMessages());

        return Arrays.stream(resolved.getAllArtifactsReports())
                .map(ArtifactDownloadReport::getLocalFile)
                .collect(Collectors.toList());
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
        jars.forEach(jar -> {
            System.out.println("Adding " + jar);
            this.addToClasspath.accept(jar);
        });
    }

    @LineMagic(aliases = { "addMavenDependency", "maven" })
    public void addMavenDependencies(List<String> args) {
        for (String arg : args) {
            try {
                this.addJarsToClasspath(
                        this.resolveMavenDependency(arg).stream()
                                .map(File::getAbsolutePath)
                                ::iterator
                );
            } catch (IOException | ParseException e) {
                throw new RuntimeException(e);
            }
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
            File tempPomPath = File.createTempFile("ijava-maven-", ".pom").getAbsoluteFile();
            tempPomPath.deleteOnExit();

            String rawPom = this.solidifyPartialPOM(body);
            Files.write(tempPomPath.toPath(), rawPom.getBytes(Charset.forName("utf-8")));

            List<String> loadArgs = new ArrayList<>(args.size() + 1);
            loadArgs.add(tempPomPath.getAbsolutePath());
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

        String pomPath = args.get(0);
        List<String> scopes = args.subList(1, args.size());

        File pomFile = new File(pomPath);
        try {
            File ivyFile = this.convertPomToIvy(pomFile);

            Ivy ivy = this.createDefaultIvyInstance();
            IvySettings settings = ivy.getSettings();
            this.addPomReposToIvySettings(settings, pomFile);

            this.addJarsToClasspath(
                    this.resolveFromIvyFile(ivy, ivyFile, scopes).stream()
                            .map(File::getAbsolutePath)
                            ::iterator
            );
        } catch (IOException | ParseException | ModelBuildingException e) {
            throw new RuntimeException(e);
        }
    }
}
