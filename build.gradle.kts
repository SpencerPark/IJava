import com.hierynomus.gradle.license.tasks.LicenseFormat
import nl.javadude.gradle.plugins.license.LicenseExtension
import org.apache.tools.ant.filters.ReplaceTokens
import com.github.jk1.license.render.*
import com.github.jk1.license.filter.*

plugins {
    java
    `maven-publish`
    id("com.github.hierynomus.license") version "0.14.0"
    id("io.github.spencerpark.jupyter-kernel-installer") version "1.1.5"
    id("com.github.jk1.dependency-license-report") version "1.1"
}

group = "io.github.spencerpark"
version = "1.1.0-SNAPSHOT"

tasks.getByName<Wrapper>("wrapper") {
    gradleVersion = "4.8.1"
}

// Add the license header to source files
license {
    header = file("LICENSE")
    exclude("**/*.json")
    // Use a regular multiline comment rather than a javadoc comment
    mapping("java", "SLASHSTAR_STYLE")
}
tasks.getByName("build").dependsOn("licenseFormat")

// Configures the license report generated for the dependencies.
licenseReport {
    //TODO write a renderer based on the html one with less opinionated styles
    renderers = arrayOf(
            // Generate a pretty HTML report that groups dependencies by their license.
            InventoryHtmlReportRenderer("dependencies.html"),
            TextReportRenderer("dependencies.txt"),
            // TODO make sure ci verifies that all licenses are know to be allowed to redistribute before publishing
            JsonReportRenderer("dependencies.json")
    )

    // Group same licenses despite names being slightly different (ex. Apache 2.0 vs Apache version 2)
    filters = arrayOf(LicenseBundleNormalizer())

    // Include the report for shaded dependencies as these are what will be redistributed
    // and therefore must go in the report which will also be distributed.
    configurations = arrayOf("shade")
}

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_1_9.toString()
    targetCompatibility = JavaVersion.VERSION_1_9.toString()
}

val shade by configurations.creating {
    // transitive true to make sure that the dependencies of shade dependencies also get shaded
    // into the jar
    isTransitive = true
    configurations["compile"].extendsFrom(this)
}

repositories {
    maven {
        name = "oss sonatype snapshots"
        setUrl("https://oss.sonatype.org/content/repositories/snapshots/")
    }
    mavenCentral()
    mavenLocal()
}

dependencies {
    shade(group = "io.github.spencerpark", name = "jupyter-jvm-basekernel", version = "2.2.1-SNAPSHOT")
    shade(group = "org.jboss.shrinkwrap.resolver", name = "shrinkwrap-resolver-impl-maven", version = "3.1.3")

    testCompile(group = "junit", name = "junit", version = "4.12")
}

tasks.withType<Jar> {
    //Include all shaded dependencies in the jar
    from(shade.map {  if (it.isDirectory) it  else  zipTree(it) })


    manifest {
        attributes(mapOf(
                "Main-class" to "io.github.spencerpark.ijava.IJava"
        ))
    }
}

tasks.getByName<ProcessResources>("processResources") {
    val tokens = mapOf(
            "version" to project.version,
            "project" to project.name
    )
    inputs.properties(tokens)
    filter(mapOf("tokens" to tokens), ReplaceTokens::class.java)
}

publishing {
    (publications) {
        "mavenJava"(MavenPublication::class) {
            from(components["java"])
        }
    }
}

jupyter {
    kernelName = "java"
    kernelDisplayName = "Java"
    kernelLanguage = "java"
    kernelInterruptMode = "message"
}