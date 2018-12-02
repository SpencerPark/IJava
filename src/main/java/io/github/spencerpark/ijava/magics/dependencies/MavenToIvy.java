package io.github.spencerpark.ijava.magics.dependencies;

import org.apache.ivy.plugins.resolver.ChainResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.ModelBuildingException;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public class MavenToIvy {
    public static List<DependencyResolver> getRepositoriesFromModel(CharSequence pom) throws ModelBuildingException {
        return MavenToIvy.getRepositoriesFromModel(Maven.getInstance().readEffectiveModel(pom).getEffectiveModel());
    }

    public static List<DependencyResolver> getRepositoriesFromModel(File pom) throws ModelBuildingException {
        return MavenToIvy.getRepositoriesFromModel(Maven.getInstance().readEffectiveModel(pom).getEffectiveModel());
    }

    public static List<DependencyResolver> getRepositoriesFromModel(Model model) {
        return model.getRepositories().stream()
                .map(MavenToIvy::convertRepository)
                .collect(Collectors.toList());
    }

    public static DependencyResolver convertRepository(Repository repository) {
        return CommonRepositories.maven(repository.getId(), repository.getUrl());
    }

    public static ChainResolver createChainForModelRepositories(Model model) {
        ChainResolver resolver = new ChainResolver();

        // Maven central is always an implicit repository.
        resolver.add(CommonRepositories.mavenCentral());

        MavenToIvy.getRepositoriesFromModel(model).forEach(resolver::add);

        return resolver;
    }
}
