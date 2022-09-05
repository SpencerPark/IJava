package io.github.spencerpark.ijava.utils;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;

import java.io.File;
import java.util.*;

public class ResolveDependency {
    private static final String DEFAULT_REPO_LOCAL = String.format("%s/.m2/repository", System.getProperty("user.home"));
    private static final RemoteRepository DEFAULT_REPO_REMOTE = new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/").build();
    private static final Set<String> DEFAULT_SCOPES = Set.of("", JavaScopes.COMPILE);

    private static RepositorySystem system;

    public static List<String> resolve(String... coords) throws DependencyResolutionException {
        return resolve(List.of(DEFAULT_REPO_REMOTE), DEFAULT_REPO_LOCAL, coords);
    }

    public static List<String> resolve(List<RemoteRepository> remoteRepos, String localRepo, String... coords) throws DependencyResolutionException {
        if (Objects.isNull(coords) || coords.length == 0) return Collections.emptyList();
        if (system == null) system = buildRepositorySystem();

        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, new LocalRepository(localRepo)));
        session.setDependencySelector(buildDependencySelector(DEFAULT_SCOPES));

        List<Dependency> dependencies = Arrays.stream(coords).map(DefaultArtifact::new).map(artifact -> new Dependency(artifact, null)).toList();
        CollectRequest collectRequest = new CollectRequest(dependencies, null, remoteRepos);
        DependencyRequest request = new DependencyRequest(collectRequest, DependencyFilterUtils.classpathFilter(DEFAULT_SCOPES));
        DependencyResult result = system.resolveDependencies(session, request);

        PreorderNodeListGenerator nodeListGenerator = new PreorderNodeListGenerator();
        result.getRoot().accept(nodeListGenerator);

        return nodeListGenerator.getFiles().stream().map(File::getAbsolutePath).toList();
    }

    private static DependencySelector buildDependencySelector(final Collection<String> scopes) {
        return new DependencySelector() {
            @Override
            public boolean selectDependency(Dependency dependency) {
                return scopes.contains(dependency.getScope()) && Boolean.FALSE.equals(dependency.getOptional());
            }

            @Override
            public DependencySelector deriveChildSelector(DependencyCollectionContext context) {
                return this;
            }
        };
    }

    private static RepositorySystem buildRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        return locator.getService(RepositorySystem.class);
    }

    public static void main(String[] args) {
        test();
    }

    public static void test() {
        String localRepo = "out";
        List<RemoteRepository> remotes = List.of(
                DEFAULT_REPO_REMOTE,
                new RemoteRepository.Builder("aliyun", "default", "https://maven.aliyun.com/repository/central").build()
        );
        try {
            List<String> jars = resolve(remotes, localRepo, "org.apache.logging.log4j:log4j-core:2.17.0");
            System.out.printf(">>>>>> jars: %s%n", jars);
        } catch (DependencyResolutionException e) {
            e.printStackTrace();
        }
    }
}
