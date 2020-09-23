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
package io.github.spencerpark.ijava.magics;

import io.github.spencerpark.ijava.magics.dependencies.CommonRepositories;
import io.github.spencerpark.ijava.magics.dependencies.Maven;
import io.github.spencerpark.ijava.magics.dependencies.MavenToIvy;
import io.github.spencerpark.jupyter.api.magic.registry.CellMagic;
import io.github.spencerpark.jupyter.api.magic.registry.LineMagic;
import io.github.spencerpark.jupyter.api.magic.registry.MagicsArgs;
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
import org.apache.ivy.util.MessageLogger;
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
import java.net.MalformedURLException;
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
     * is still usually a ".jar" file but that corresponds to the "ext" not the "type".
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
        this.repos.add(CommonRepositories.mavenLocal());
    }

    public void addRemoteRepo(String name, String url) {
        if (DEFAULT_RESOLVER_NAME.equals(name))
            throw new IllegalArgumentException("Illegal repository name, cannot use '" + DEFAULT_RESOLVER_NAME + "'.");

        this.repos.add(CommonRepositories.maven(name, url));
    }

    private ChainResolver searchAllReposResolver(Set<String> repos) {
        ChainResolver resolver = new ChainResolver();
        resolver.setName(DEFAULT_RESOLVER_NAME);

        this.repos.stream()
                .filter(r -> repos == null || repos.contains(r.getName().toLowerCase()))
                .forEach(resolver::add);

        if (repos != null) {
            Set<String> resolverNames = resolver.getResolvers().stream()
                    .map(d -> d.getName().toLowerCase())
                    .collect(Collectors.toSet());
            repos.removeAll(resolverNames);

            repos.forEach(r -> {
                try {
                    URL url = new URL(r);
                    resolver.add(CommonRepositories.maven("from-" + url.getHost(), r));
                } catch (MalformedURLException e) {
                    // Ignore as we will assume that a bad url was a name
                }
            });
        }

        return resolver;
    }

    private static ModuleRevisionId parseCanonicalArtifactName(String canonical) {
        Matcher m = IVY_MRID_PATTERN.matcher(canonical);
        if (m.matches()) {
            return ModuleRevisionId.newInstance(
                    m.group("organization"),
                    m.group("name"),
                    m.group("branch"),
                    m.group("revision")
            );
        }

        m = MAVEN_MRID_PATTERN.matcher(canonical);
        if (m.matches()) {
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

    /**
     * Create an ivy instance with the specified verbosity. The instance is relatively plain.
     *
     * @param verbosity the verbosity level.
     *                  <ol start="0">
     *                  <li>ERROR</li>
     *                  <li>WANRING</li>
     *                  <li>INFO</li>
     *                  <li>VERBOSE</li>
     *                  <li>DEBUG</li>
     *                  </ol>
     *
     * @return the fresh ivy instance.
     */
    private Ivy createDefaultIvyInstance(int verbosity) {
        MessageLogger logger = new DefaultMessageLogger(verbosity);

        // Set the default logger since not all things log to the ivy instance.
        Message.setDefaultLogger(logger);
        Ivy ivy = new Ivy();

        ivy.getLoggerEngine().setDefaultLogger(logger);
        ivy.setSettings(new IvySettings());
        ivy.bind();

        return ivy;
    }

    // TODO support multiple at once. This is necessary for conflict resolution with multiple overlapping dependencies.
    // TODO support classpath resolution
    public List<File> resolveMavenDependency(String canonical, Set<String> repos, int verbosity) throws IOException, ParseException {
        ChainResolver rootResolver = this.searchAllReposResolver(repos);

        Ivy ivy = this.createDefaultIvyInstance(verbosity);
        IvySettings settings = ivy.getSettings();

        settings.addResolver(rootResolver);
        rootResolver.setCheckmodified(true);
        settings.setDefaultResolver(rootResolver.getName());

        ivy.getLoggerEngine().info("Searching for dependencies in: " + rootResolver.getResolvers());

        ResolveOptions resolveOptions = new ResolveOptions();
        resolveOptions.setTransitive(true);
        resolveOptions.setDownload(true);

        ModuleRevisionId artifactIdentifier = MavenResolver.parseCanonicalArtifactName(canonical);
        DefaultModuleDescriptor containerModule = DefaultModuleDescriptor.newCallerInstance(
                artifactIdentifier,
                DEFAULT_RESOLVE_CONFS,
                true, // Transitive
                repos != null // Changing - the resolver will set this based on SNAPSHOT since they are all m2 compatible
                // but if `repos` is specified, we want to force a lookup.
        );

        ResolveReport resolved = ivy.resolve(containerModule, resolveOptions);
        if (resolved.hasError()) {
            MessageLogger logger = ivy.getLoggerEngine();
            Arrays.stream(resolved.getAllArtifactsReports())
                    .forEach(r -> {
                        logger.error("download " + r.getDownloadStatus() + ": " + r.getArtifact() + " of " + r.getType());
                        if (r.getArtifactOrigin() == null)
                            logger.error("\tCouldn't find artifact.");
                        else
                            logger.error("\tfrom: " + r.getArtifactOrigin());
                    });

            // TODO better error...
            throw new RuntimeException("Error resolving '" + canonical + "'. " + resolved.getAllProblemMessages());
        }

        return Arrays.stream(resolved.getAllArtifactsReports())
                .filter(a -> JAR_TYPE.equalsIgnoreCase(a.getType()))
                .map(ArtifactDownloadReport::getLocalFile)
                .collect(Collectors.toList());
    }

    private File convertPomToIvy(Ivy ivy, File pomFile) throws IOException, ParseException {
        PomModuleDescriptorParser parser = PomModuleDescriptorParser.getInstance();

        URL pomUrl = pomFile.toURI().toURL();

        ModuleDescriptor pomModule = parser.parseDescriptor(new IvySettings(), pomFile.toURI().toURL(), false);

        File tempIvyFile = File.createTempFile("ijava-ivy-", ".xml").getAbsoluteFile();
        tempIvyFile.deleteOnExit();

        parser.toIvyFile(pomUrl.openStream(), new URLResource(pomUrl), tempIvyFile, pomModule);

        MessageLogger logger = ivy.getLoggerEngine();
        logger.info(new String(Files.readAllBytes(tempIvyFile.toPath()), Charset.forName("utf8")));

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
        jars.forEach(this.addToClasspath);
    }

    @LineMagic(aliases = { "addMavenDependency", "maven" })
    public void addMavenDependencies(List<String> args) {
        MagicsArgs schema = MagicsArgs.builder()
                .varargs("deps")
                .keyword("from")
                .flag("verbose", 'v')
                .onlyKnownKeywords()
                .onlyKnownFlags()
                .build();

        Map<String, List<String>> vals = schema.parse(args);

        List<String> deps = vals.get("deps");
        List<String> from = vals.get("from");
        int verbosity = vals.get("verbose").size();

        Set<String> repos = from.isEmpty() ? null : new LinkedHashSet<>(from);


        for (String dep : deps) {
            try {
                this.addJarsToClasspath(
                        this.resolveMavenDependency(dep, repos, verbosity).stream()
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
        MagicsArgs schema = MagicsArgs.builder().required("id").required("url").build();
        Map<String, List<String>> vals = schema.parse(args);

        String id = vals.get("id").get(0);
        String url = vals.get("url").get(0);

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

        MagicsArgs schema = MagicsArgs.builder()
                .required("pomPath")
                .varargs("scopes")
                .flag("verbose", 'v')
                .onlyKnownKeywords().onlyKnownFlags().build();

        Map<String, List<String>> vals = schema.parse(args);

        String pomPath = vals.get("pomPath").get(0);
        List<String> scopes = vals.get("scopes");
        int verbosity = vals.get("verbose").size();

        File pomFile = new File(pomPath);
        try {
            Ivy ivy = this.createDefaultIvyInstance(verbosity);
            IvySettings settings = ivy.getSettings();

            File ivyFile = this.convertPomToIvy(ivy, pomFile);

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
