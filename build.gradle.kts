import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.JsonReportRenderer
import io.github.spencerpark.gradle.NewInventoryHtmlReportRenderer
import nl.javadude.gradle.plugins.license.LicenseExtension
import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    id("java")
    id("maven-publish")
    id("com.github.hierynomus.license") version "0.16.1"
    id("io.github.spencerpark.jupyter-kernel-installer") version "3.0.0-SNAPSHOT"
    id("com.github.jk1.dependency-license-report")
}

group = "io.github.spencerpark"
version = "1.3.0"

// Add the license header to source files
configure<LicenseExtension> {
    header = project.file("LICENSE")
    exclude("**/*.json")
    // Use a regular multiline comment rather than a javadoc comment
    mapping("java", "SLASHSTAR_STYLE")
}

// Configures the license report generated for the dependencies.
licenseReport {
    excludeGroups = arrayOf()
    renderers = arrayOf(
        // Generate a pretty HTML report that groups dependencies by their license.
        NewInventoryHtmlReportRenderer("dependencies.html"),
        // TODO make sure ci verifies that all licenses are know to be allowed to redistribute before publishing
        JsonReportRenderer("dependencies.json")
    )

    // Group same licenses despite names being slightly different (ex. Apache 2.0 vs Apache version 2)
    filters = arrayOf(LicenseBundleNormalizer())

    configurations = arrayOf("compile")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_9
    targetCompatibility = JavaVersion.VERSION_1_9
}

configurations {
    create("shade") {
        // transitive true to make sure that the dependencies of shade dependencies also get shaded
        // into the jar
        isTransitive = true
        implementation.get().extendsFrom(this)
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
    }
}

dependencies {
    "shade"(group = "io.github.spencerpark", name = "jupyter-jvm-basekernel", version = "2.4.0-SNAPSHOT")

    "shade"(group = "org.apache.ivy", name = "ivy", version = "2.5.0-rc1")
    //shade group= "org.apache.maven", name= "maven-settings-builder", version= "3.6.0"
    "shade"(group = "org.apache.maven", name = "maven-model-builder", version = "3.6.0")

    testImplementation(group = "junit", name = "junit", version = "4.12")
}

tasks.jar {
    //Include all shaded dependencies in the jar except META-INF to avoid conflicts
    from(configurations["shade"].map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF", "META-INF/**")
    }

    manifest {
        attributes("Main-class" to "io.github.spencerpark.ijava.IJava")
    }
}

tasks.processResources {
    val tokens = mapOf(
        "version" to project.version,
        "project" to project.name,
    )
    inputs.properties(tokens)

    filesMatching("ijava-kernel-metadata.json") {
        filter(mapOf("tokens" to tokens), ReplaceTokens::class.java)
    }
}

publishing {
    publications {
        create("mavenJava", MavenPublication::class.java) {
            from(components["java"])
        }
    }
}

jupyter {
    kernelName = "java"
    kernelDisplayName = "Java"
    kernelLanguage = "java"
    kernelInterruptMode = "message"

    kernelParameters {
        list("classpath", "IJAVA_CLASSPATH") {
            separator = PATH_SEPARATOR
            description =
                """A file path separator delimited list of classpath entries that should be available to the user code. **Important:** no matter what OS, this should use forward slash "/" as the file separator. Also each path may actually be a simple glob."""
        }

        list("comp-opts", "IJAVA_COMPILER_OPTS") {
            separator = " "
            description =
                """A space delimited list of command line options that would be passed to the `javac` command when compiling a project. For example `-parameters` to enable retaining parameter names for reflection."""
        }

        list("startup-scripts-path", "IJAVA_STARTUP_SCRIPTS_PATH") {
            separator = PATH_SEPARATOR
            description =
                """A file path seperator delimited list of `.jshell` scripts to run on startup. This includes ijava-jshell-init.jshell and ijava-display-init.jshell. **Important:** no matter what OS, this should use forward slash "/" as the file separator. Also each path may actually be a simple glob."""
        }

        string("startup-script", "IJAVA_STARTUP_SCRIPT") {
            description =
                """A block of java code to run when the kernel starts up. This may be something like `import my.utils;` to setup some default imports or even `void sleep(long time) { try {Thread.sleep(time); } catch (InterruptedException e) { throw new RuntimeException(e); }}` to declare a default utility method to use in the notebook."""
        }

        string("timeout", "IJAVA_TIMEOUT") {
            aliases["NO_TIMEOUT"] = "-1"
            description =
                """A duration specifying a timeout (in milliseconds by default) for a _single top level statement_. If less than `1` then there is no timeout. If desired a time may be specified with a `TimeUnit` may be given following the duration number (ex `"30 SECONDS"`)."""
        }
    }

    kernelResources {
        from(tasks.named("generateLicenseReport")) {
            into("dependency-licenses")
        }
    }
}

tasks.installKernel {
    kernelInstallPath = commandLineSpecifiedPathOr(userInstallPath)
//    kernelInstallPath = commandLineSpecifiedPathOr(layout.buildDirectory.dir("jupyter/mock-install"))
}
