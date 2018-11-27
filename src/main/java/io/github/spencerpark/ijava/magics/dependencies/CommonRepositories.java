package io.github.spencerpark.ijava.magics.dependencies;

import org.apache.ivy.plugins.repository.file.FileRepository;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.IBiblioResolver;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommonRepositories {
    private static Path getUserHomePath() {
        String home = System.getProperty("user.home");
        return Paths.get(home).toAbsolutePath();
    }

    private static final Pattern MAVEN_VAR_PATTERN = Pattern.compile("\\$\\{(?<name>[^}*])}");

    private static String replaceMavenVars(String raw) {
        StringBuilder replaced = new StringBuilder();

        Matcher matcher = MAVEN_VAR_PATTERN.matcher(raw);
        while (matcher.find())
            matcher.appendReplacement(replaced,
                    System.getProperty(matcher.group("name"), ""));

        matcher.appendTail(replaced);

        return replaced.toString();
    }

    // Thanks gradle!

    private static Path getUserMavenHomePath() {
        return CommonRepositories.getUserHomePath().resolve(".m2");
    }

    private static Path getGlobalMavenHomePath() {
        String envM2Home = System.getenv("M2_HOME");
        return envM2Home != null
                ? Paths.get(envM2Home).toAbsolutePath() : null;
    }

    private static Path getUserMavenSettingsPath() {
        return CommonRepositories.getUserMavenHomePath().resolve("settings.xml");
    }

    private static Path getGlobalMavenSettingsPath() {
        Path sysHome = CommonRepositories.getGlobalMavenHomePath();
        return sysHome != null ? sysHome.resolve("conf").resolve("settings.xml") : null;
    }

    private static Path getDefaultMavenLocalRepoPath() {
        return CommonRepositories.getUserMavenHomePath().resolve("repository");
    }

    private static Path readConfiguredLocalRepositoryPath(Path settingsXmlPath) throws IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);

        DocumentBuilder builder;
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            // We are configuring the factory, the configuration will be fine...
            e.printStackTrace();
            return null;
        }

        try (InputStream in = Files.newInputStream(settingsXmlPath)) {
            Document settingsDoc = builder.parse(in);
            NodeList settings = settingsDoc.getElementsByTagName("settings");
            if (settings.getLength() == 0)
                return null;

            for (int i = 0; i < settings.getLength(); i++) {
                Node setting = settings.item(i);
                switch (setting.getNodeName()) {
                    case "localRepository":
                        String localRepository = setting.getTextContent();
                        localRepository = CommonRepositories.replaceMavenVars(localRepository);
                        return Paths.get(localRepository);
                }
            }
        }

        return null;
    }

    private static Path getMavenLocalRepositoryPath() throws IOException, SAXException {
        Path userSettingsXmlPath = CommonRepositories.getUserMavenSettingsPath();
        Path path = CommonRepositories.readConfiguredLocalRepositoryPath(userSettingsXmlPath);

        if (path == null) {
            Path globalSettingsXmlPath = CommonRepositories.getGlobalMavenSettingsPath();
            if (globalSettingsXmlPath != null)
                path = CommonRepositories.readConfiguredLocalRepositoryPath(globalSettingsXmlPath);
        }

        return path == null ? CommonRepositories.getDefaultMavenLocalRepoPath() : path;
    }

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
            localRepoPath = CommonRepositories.getMavenLocalRepositoryPath();
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
