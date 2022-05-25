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
