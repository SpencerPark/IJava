# IJava

[![Binder](https://mybinder.org/badge.svg)](https://mybinder.org/v2/gh/SpencerPark/ijava-binder/master?filepath=%2Fhome%2Fjovyan%2FHelloWorld.ipynb)

A [Jupyter](http://jupyter.org/) kernel for executing Java code. The kernel executes code via the new [JShell tool](https://docs.oracle.com/javase/9/jshell/introduction-jshell.htm). Some of the additional commands should be supported in the future via a syntax similar to the ipython magics.

The kernel is currently working but there are some features that would be nice to have. There is a [TODO list](#todo) of planned features but any additional requests for new ones or prioritizing current ones are welcomed in the [issues](https://github.com/SpencerPark/IJava/issues).

If you are interested in building your own kernel that runs on the JVM check out the related project that this kernel is build on, [jupyter-jvm-basekernel](https://github.com/SpencerPark/jupyter-jvm-basekernel).

For Maven dependency resolution, the kernel is using [ShrinkWrap resolvers](https://github.com/shrinkwrap/resolver).

### Contents

*   [Try online](#try-online)
*   [Features](#features)
    *   [TODO](#todo)
*   [Requirements](#requirements)
*   [Installing](#installing)
*   [Configuring](#configuring)
    *   [List of options](#list-of-options)
    *   [Changing VM/compiler options](#changing-vmcompiler-options)
    *   [Configuring startup scripts](#configuring-startup-scripts)
*   [Run](#run)

### Try Online

Clicking on the [![Binder](https://mybinder.org/badge.svg)](https://mybinder.org/v2/gh/SpencerPark/ijava-binder/master?filepath=%2Fhome%2Fjovyan%2FHelloWorld.ipynb) badge at the top (or right here) will spawn a jupyter server running this kernel. The binder base is the [ijava-binder project](https://github.com/SpencerPark/ijava-binder).

### Features

Currently the kernel supports

*   Code execution.
    ![output](docs/img/output.png)
*   Autocompletion (`TAB` in Jupyter notebook).
    ![autocompletion](docs/img/autocompletion.png)
*   Code inspection (`Shift-TAB` up to 4 times in Jupyter notebook).
    ![code-inspection](docs/img/code-inspection.png)
*   Colored, friendly, error message displays.
    ![compilation-error](docs/img/compilation-error.png)
    ![incomplete-src-error](docs/img/incomplete-src-error.png)
    ![runtime-error](docs/img/runtime-error.png)
*   Add maven dependencies at runtime (See also [magics.md](docs/magics.md)).
    ![maven-pom-dep](docs/img/maven-pom-dep.png)
*   Display rich output (See also [display.md](docs/display.md) and [maven magic](docs/magics.md#addMavenDependencies)). Chart library in the demo photo is [XChart](https://github.com/knowm/XChart) with the sample code taken from their README.
    ![display-img](docs/img/display-img.png)
*   Configurable evaluation timeout
    ![timeout](docs/img/timeout.png)

#### TODO

- [ ] Support magics for making queries about the current environment.
- [ ] Compile javadocs when displaying introspection requests as html.

### Requirements

1.  [Java JDK >=9](http://www.oracle.com/technetwork/java/javase/downloads/index.html). **Not the JRE**

    Ensure that the `java` command is in the PATH and is using version 9. For example:
    ```bash
    > java -version
    java version "9"
    Java(TM) SE Runtime Environment (build 9+181)
    Java HotSpot(TM) 64-Bit Server VM (build 9+181, mixed mode)
    ```

    If the kernel cannot start with an error along the lines of
    ```text
    Exception in thread "main" java.lang.NoClassDefFoundError: jdk/jshell/JShellException
            ...
    Caused by: java.lang.ClassNotFoundException: jdk.jshell.JShellException
            ...
    ```
    then double check that `java` is referring to the command for the `jdk` and not the `jre`.
    
2.  Some jupyter-like environment to use the kernel in.

    A non-exhaustive list of options:

    *   [Jupyter](http://jupyter.org/install) - main option
    *   [JupyterLab](http://jupyterlab.readthedocs.io/en/stable/getting_started/installation.html)
    *   [nteract](https://nteract.io/desktop)
        
### Installing

After meeting the [requirements](#requirements), the kernel can be installed locally.

1.  Download the project.
    ```bash
    > git clone https://github.com/SpencerPark/IJava.git --depth 1
    > cd IJava/
    ```
2.  Build and install the kernel.
    
    On *nix `chmod u+x gradlew && ./gradlew installKernel`
        
    On windows `gradlew installKernel`

### Configuring

Configuring the kernel can be done via environment variables. These can be set on the system or inside the `kernel.json`. To find where the kernel is installed run

```bash
> jupyter kernelspec list
Available kernels:
  java           .../kernels/java
  python3        .../python35/share/jupyter/kernels/python3
```

and the `kernel.json` file will be in the given directory.

#### List of options

`IJAVA_COMPILER_OPTS` - **default: `""`** - A space delimited list of command line options that would be passed to the `javac` command when compiling a project. For example `-parameters` to enable retaining parameter names for reflection.

`IJAVA_TIMEOUT` - **default: `"-1"`** - A duration specifying a timeout (in milliseconds by default) for a _single top level statement_. If less than `1` then there is no timeout. If desired a time may be specified with a [`TimeUnit`](https://docs.oracle.com/javase/9/docs/api/java/util/concurrent/TimeUnit.html) may be given following the duration number (ex `"30 SECONDS"`).

`IJAVA_CLASSPATH` - **default: `""`** - A file path separator delimited list of classpath entries that should be available to the user code. **Important:** no matter what OS, this should use forward slash "/" as the file separator. Also each path may actually be a [simple glob](#simple-glob-syntax).

`IJAVA_STARTUP_SCRIPTS_PATH` - **default: `""`** - A file path seperator delimited list of `.jshell` scripts to run on startup. This includes [ijava-jshell-init.jshell](src/main/resources/ijava-jshell-init.jshell) and [ijava-display-init.jshell](src/main/resources/ijava-display-init.jshell). **Important:** no matter what OS, this should use forward slash "/" as the file separator. Also each path may actually be a [simple glob](#simple-glob-syntax).

`IJAVA_STARTUP_SCRIPT` - **default: `""`** - A block of java code to run when the kernel starts up. This may be something like `import my.utils;` to setup some default imports or even `void sleep(long time) { try {Thread.sleep(time); } catch (InterruptedException e) { throw new RuntimeException(e); }}` to declare a default utility method to use in the notebook.

##### Simple glob syntax

Options that support this glob syntax may reference a set of files with a single path-like string. Basic glob queries are supported including:

*   `*` to match 0 or more characters up to the next path boundary `/`
*   `?` to match a single character
*   A path ending in `/` implicitly adds a `*` to match all files in the resolved directory

Any relative paths are resolved from the notebook server's working directory. For example the glob `*.jar` will match all jars is the directory that the `jupyter notebook` command was run.

**Note:** users on any OS should use `/` as a path separator.

#### Changing VM/compiler options

See the [List of options](#list-of-options) section for all of the configuration options.

To change compiler options use the `IJAVA_COMPILER_OPTS` environment variable with a string of flags as if running the `javac` command.

The `IJAVA_COMPILER_OPTS` and kernel VM parameters can be assigned in the `kernel.json` by adding/editing a JSON dictionary at the `env` key and changing the `argv` list.

For example to enable assertions, set a limit on the heap size to `128m`, and enable parameter names in reflection:

```diff
{
- "argv": [ "java", "-jar", "{connection_file}"],
+ "argv": [ "java", "-ea", "-Xmx128m", "-jar", "{connection_file}"],
  "display_name": "Java",
  "language": "java",
  "env": {
+     "IJAVA_COMPILER_OPTS" : "-parameter"
  }
}
```

#### Configuring startup scripts

See the [List of options](#list-of-options) section for all of the configuration options.

To setup a startup script such as an `init.jshell` script, set the `IJAVA_STARTUP_SCRIPTS_PATH` to `init.jshell` in the `kernel.json`. This will try to execute an `init.jshell` script in the working directory of kernel.

If desired use an absolute path to use a global init file.

```diff
{
  "argv": [ "java", "-jar", "{connection_file}"],
  "display_name": "Java",
  "language": "java",
  "env": {
+     "IJAVA_STARTUP_SCRIPTS_PATH": "init.jshell"
  }
```

### Run

This is where the documentation diverges, each environment has it's own way of selecting a kernel. To test from command line with Jupyter's console application run:

```bash
jupyter console --kernel=java
```

Then at the prompt try:
```java
In [1]: String helloWorld = "Hello world!"

In [2]: helloWorld
Out[2]: "Hello world!"
```
