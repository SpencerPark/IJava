# Magics

Magics in IJava are very similar to those from IPython. There are:

*   **Line magics**: which are inline function calls via a magic function.

    ```text
    %mavenRepo oss-sonatype-snapshots https://oss.sonatype.org/content/repositories/snapshots/
    %maven io.github.spencerpark:jupyter-jvm-basekernel:2.0.0-SNAPSHOT
    List<String> addedJars = %jars C:/all/my/*.jar
    ```

*   **Cell magics**: which are entire cell function calls that use the body of the cell as a special argument.

    ```xml
    %%loadFromPOM
    <repository>
      <id>oss-sonatype-snapshots</id>
      <url>https://oss.sonatype.org/content/repositories/snapshots/</url>
    </repository>

    <dependency>
      <groupId>io.github.spencerpark</groupId>
      <artifactId>jupyter-jvm-basekernel</artifactId>
      <version>2.0.0-SNAPSHOT</version>
    </dependency>
    ```

The magics simply desugar to calls to `lineMagic` and `cellMagic` in case programmatic access is desired. These functions are in the notebook namespace and have the signatures below. Note the return type which allows for an implicit cast to what ever type is required but there is no safety in these checks.

*   `<T> T lineMagic(String name, java.util.List<String> args)`
*   `<T> T cellMagic(String name, java.util.List<String> args, String body)`

## Magics provided by IJava

Things that are likely to become magics are kernel meta functions or functions that operate on source code. Magics should only be used for things that only appear in a Jupyter-like context and only use string arguments. Other things (like `display` and `render`) should be provided as plain functions.



### jars

Add jars to the notebook classpath.

###### Line magic

*   **arguments**:
    *   _varargs_ list of simple glob paths to jars on the local file system. If a glob matches a directory all files in that directory will be added.



### classpath

Add entries to the notebook classpath.

###### Line magic

*   **arguments**:
    *   _varargs_ list of simple glob paths to entries on the local file system. This includes directories or jars.



### addMavenDependencies

Add maven artifacts to the notebook classpath. All transitive dependencies are also added to the classpath. See also [addMavenRepo](#addmavenrepo).

###### Line magic

*   **aliases**: `addMavenDependency`, `maven`
*   **arguments**:
    *   _varargs_ list of dependency coordinates in the form `groupId:artifactId:[packagingType:[classifier]]:version`



### addMavenRepo

Add a maven repository to search for when using [addMavenDependencies](#addmavendependencies).

###### Line magic

*   **aliases**: `mavenRepo`
*   **arguments**:
    *   repository id
    *   repository url


### loadFromPOM

Load any dependencies specified in a POM. This **ignores** repositories added with [addMavenRepo](#addmavenrepo) as the POM would likely specify it's own.

The cell magic is designed to make it very simple to copy and paste from any READMEs specifying maven POM fragments to use in depending on an artifact (including repositories other than central).

###### Line magic

*   **arguments**:
    *   path to local POM file
    *   _varargs_ list of scope types to filter the dependencies by. Defaults to `compile`, `runtime`, `system`, and `import` if not supplied.

###### Cell magic

*   **arguments**:
    *   _varargs_ list of scope types to filter the dependencies by. Defaults to `compile`, `runtime`, `system`, and `import` if not supplied.
*   **body**:
    A _partial_ POM literal.

    If the body is an xml `<project>` tag, then the body is used as a POM without being modified.

    Otherwise, the magic attempts to build a POM based on the xml fragments it gets.

    `<modelVersion>`, `<groupId>`, `<artifactId>`, and `<version>` are given default values if not supplied which there is no reason to supply other than if they happen to be what is copy-and-pasted.

    All children of `<dependencies>` and `<repositories>` are collected **along with any loose `<dependency>` and `repository` tags**.

    Ex: To add a dependency not in central simply add a valid `<repository>` and `<dependency>` and the magic will take care of putting it together into a POM.

    ```xml
    %%loadFromPOM
    <repository>
      <id>oss-sonatype-snapshots</id>
      <url>https://oss.sonatype.org/content/repositories/snapshots/</url>
    </repository>

    <dependency>
      <groupId>io.github.spencerpark</groupId>
      <artifactId>jupyter-jvm-basekernel</artifactId>
      <version>2.0.0-SNAPSHOT</version>
    </dependency>
    ```
    
### exec

Allow user to execute system program in jupyter notebook.

###### Line magic

*   **aliases**: `system`
*   **arguments**:
    *   _varargs_ list of external program and its command line arguments to be executed
