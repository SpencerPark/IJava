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
package io.github.spencerpark.ijava.magics.dependencies;

import io.github.spencerpark.ijava.utils.FileUtils;
import org.apache.maven.building.StringSource;
import org.apache.maven.model.building.*;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Maven {
    private static final Pattern MAVEN_VAR_PATTERN = Pattern.compile("\\$\\{(?<name>[^}*])}");

    private static final Maven INSTANCE = new Maven(new Properties(), Collections.emptyMap());

    public static Maven getInstance() {
        return INSTANCE;
    }

    // User provider environment overrides.
    private final Properties properties;
    private final Map<String, String> environment;

    public Maven(Properties properties, Map<String, String> environment) {
        this.properties = properties;
        this.environment = environment;
    }

    private String getProperty(String name, String def) {
        String val = this.environment.get(name);
        if (val != null)
            return val;

        val = System.getProperty(name);
        return val != null ? val : def;
    }

    private String getProperty(String name) {
        return this.getProperty(name, null);
    }

    private String getEnv(String name, String def) {
        String val = this.environment.get(name);
        if (val != null)
            return val;

        val = System.getenv(name);
        return val != null ? val : def;
    }

    private String getEnv(String name) {
        return this.getEnv(name, null);
    }

    public Path getUserSystemHomePath() {
        String home = this.getProperty("user.home");
        return Paths.get(home).toAbsolutePath();
    }

    private String replaceMavenVars(String raw) {
        StringBuilder replaced = new StringBuilder();

        Matcher matcher = MAVEN_VAR_PATTERN.matcher(raw);
        while (matcher.find())
            matcher.appendReplacement(replaced,
                    System.getProperty(matcher.group("name"), ""));

        matcher.appendTail(replaced);

        return replaced.toString();
    }

    // Thanks gradle!

    private Path getUserHomePath() {
        return this.getUserSystemHomePath().resolve(".m2");
    }

    private Path getGlobalHomePath() {
        String envM2Home = this.getEnv("M2_HOME");
        return envM2Home != null ? Paths.get(envM2Home).toAbsolutePath() : null;
    }

    private Path getUserSettingsPath() {
        return this.getUserHomePath().resolve("settings.xml");
    }

    private Path getGlobalSettingsPath() {
        Path sysHome = this.getGlobalHomePath();
        return sysHome != null ? sysHome.resolve("conf").resolve("settings.xml") : null;
    }

    private Path getDefaultLocalRepoPath() {
        return this.getUserHomePath().resolve("repository");
    }

    private Path readConfiguredLocalRepositoryPath(Path settingsXmlPath) throws IOException, XMLStreamException {
        if (!Files.isRegularFile(settingsXmlPath)) return null;

        String localRepositoryName = "localRepository";
        String localRepositoryVal = FileUtils.readXmlElementText(settingsXmlPath, Collections.singleton(localRepositoryName))
                .get(localRepositoryName);
        if (localRepositoryVal == null) return null;
        return Paths.get(this.replaceMavenVars(localRepositoryVal));
    }

    // TODO just use the effective settings
    public Path getConfiguredLocalRepositoryPath() throws IOException, XMLStreamException {
        Path userSettingsXmlPath = this.getUserSettingsPath();
        Path path = this.readConfiguredLocalRepositoryPath(userSettingsXmlPath);

        if (path == null) {
            Path globalSettingsXmlPath = this.getGlobalSettingsPath();
            if (globalSettingsXmlPath != null)
                path = this.readConfiguredLocalRepositoryPath(globalSettingsXmlPath);
        }

        return path == null ? this.getDefaultLocalRepoPath() : path;
    }

    /*public SettingsBuildingResult readEffectiveSettings() throws SettingsBuildingException {
        DefaultSettingsBuilder settingsBuilder = new DefaultSettingsBuilderFactory().newInstance();

        SettingsBuildingRequest request = new DefaultSettingsBuildingRequest();

        request.setSystemProperties(System.getProperties());
        request.setUserProperties(this.properties);

        request.setUserSettingsFile(this.getUserSettingsPath().toFile());

        Path globalSettingsPath = this.getGlobalSettingsPath();
        request.setGlobalSettingsFile(globalSettingsPath != null ? globalSettingsPath.toFile() : null);

        return settingsBuilder.build(request);
    }*/

    public ModelBuildingResult readEffectiveModel(CharSequence pom) throws ModelBuildingException {
        return this.readEffectiveModel(req -> req.setModelSource((ModelSource) new StringSource(pom)));
    }

    public ModelBuildingResult readEffectiveModel(File pom) throws ModelBuildingException {
        return this.readEffectiveModel(req -> req.setPomFile(pom));
    }

    private ModelBuildingResult readEffectiveModel(UnaryOperator<ModelBuildingRequest> configuration) throws ModelBuildingException {
        DefaultModelBuilder modelBuilder = new DefaultModelBuilderFactory().newInstance();

        ModelBuildingRequest request = new DefaultModelBuildingRequest();

        request.setSystemProperties(System.getProperties());
        request.setUserProperties(this.properties);

        // Allow force selection of active profile
        // request.setActiveProfileIds()
        // request.setInactiveProfileIds()

        // Better error messages for bad poms
        request.setLocationTracking(true);

        // Don't run plugins, in most cases this is what we want. I don't know of any
        // that would affect the POM.
        request.setProcessPlugins(false);

        request = configuration.apply(request);

        return modelBuilder.build(request);
    }
}
