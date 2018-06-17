package io.github.spencerpark.ijava;

import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;
import io.github.spencerpark.jupyter.kernel.magic.registry.LineMagic;
import org.jboss.shrinkwrap.resolver.api.maven.ConfigurableMavenResolverSystem;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.PomEquippedResolveStage;
import org.jboss.shrinkwrap.resolver.api.maven.ScopeType;
import org.jboss.shrinkwrap.resolver.api.maven.repository.MavenRemoteRepositories;
import org.jboss.shrinkwrap.resolver.api.maven.repository.MavenRemoteRepository;
import org.jboss.shrinkwrap.resolver.api.maven.strategy.TransitiveStrategy;

import java.io.File;
import java.io.IOException;
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

        // Turn off the shrinkwrap loggers which are quite loud.
        Logger.getLogger("org.jboss.shrinkwrap.resolver.impl.maven.logging.LogTransferListener").setLevel(Level.OFF);
        Logger.getLogger("org.jboss.shrinkwrap.resolver.impl.maven.logging.LogRepositoryListener").setLevel(Level.OFF);
        Logger.getLogger("org.jboss.shrinkwrap.resolver.impl.maven.logging.LogModelProblemCollector").setLevel(Level.OFF);
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

    public void addJarsToClasspath(Iterable<String> jars) {
        jars.forEach(this.addToClasspath);
    }

    @LineMagic(aliases = { "mavenRepo", "maven_repo" })
    public void addMavenRepo(List<String> args) {
        if (args.size() != 2)
            throw new IllegalArgumentException("Expected 2 arguments: repository id and url. Got: " + args);

        String id = args.get(0);
        String url = args.get(1);

        this.addRemoteRepo(id, url);
    }

    @CellMagic
    public void loadFromPOM(List<String> args, String body) {
        try {
            Path tempPom = Files.createTempFile("ijava-maven-", ".pom");
            Files.write(tempPom, body.getBytes(Charset.forName("utf-8")));

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
            stage = stage.importRuntimeDependencies();
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
