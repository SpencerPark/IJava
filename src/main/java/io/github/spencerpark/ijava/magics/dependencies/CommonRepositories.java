package io.github.spencerpark.ijava.magics.dependencies;

import org.apache.ivy.plugins.repository.file.FileRepository;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.IBiblioResolver;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Path;

public class CommonRepositories {
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
        return CommonRepositories.maven("maven-central","https://repo.maven.apache.org/maven2/");
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

        FileRepository mavenLocalRepo = new FileRepository();
        mavenLocalRepo.setLocal(true);
        mavenLocalRepo.setBaseDir(localRepoPath.toFile());

        resolver.setRepository(mavenLocalRepo);

        return resolver;
    }
}
