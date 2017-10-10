# IJava

A [Jupyter](http://jupyter.org/) kernel for executing Java code. The kernel executes code via the new [JShell tool](https://docs.oracle.com/javase/9/jshell/introduction-jshell.htm). Some of the additional commands should be supported in the future via a syntax similar to the ipython magics.

The kernel is currently working but there are some features that would be nice to have. There is a [TODO list](#todo) of planned features but any additional requests for new ones or prioritizing current ones are welcomed in the [issues](https://github.com/SpencerPark/IJava/issues).

#### Features

Currently the kernel supports

*   Code execution
    ![output](docs/img/output.png)
*   Autocompletion (`TAB` in Jupyter notebook)
    ![autocompletion](docs/img/autocompletion.png)
*   Code inspection (`Shift-TAB` up to 4 times in Jupyter notebook)
    ![code-inspection](docs/img/code-inspection.png)
*   Colored, friendly, error message displays
    ![compilation-error](docs/img/compilation-error.png)
    ![incomplete-src-error](docs/img/incomplete-src-error.png)
    ![runtime-error](docs/img/runtime-error.png)

#### TODO

- [ ] Display additional media types like images or audio.
- [ ] Support magics for making queries about the current environment.
- [ ] Compile javadocs when displaying introspection requests as html.
- [ ] Support loading maven dependencies at runtime.

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
    
2.  [jupyter-jvm-basekernel](https://github.com/SpencerPark/jupyter-jvm-basekernel) 

    The best way to install this dependency is to maven local. The `gradlew` command is a tool that will install the correct version of [Gradle](https://gradle.org/) if not already installed. There is no need to manually install anything.
    
    1.  Download the project.
        ```bash
        > git clone https://github.com/SpencerPark/jupyter-jvm-basekernel.git --depth 1
        > cd jupyter-jvm-basekernel/
        ```
    2.  Build and install.
    
        On unix `./gradlew publishToMavenLocal`
        
        On windows `gradlew publishToMavenLocal`
        
3.  Some jupyter-like environment to use the kernel in.
        
### Installing

After meeting the [requirements](#requirements), the kernel can be installed locally.

1.  Download the project.
    ```bash
    > git clone https://github.com/SpencerPark/IJava.git --depth 1
    > cd IJava/
    ```
2.  Build and install the kernel.
    
    On unix* `./gradlew installKernel`
        
    On windows `gradlew installKernel`
    
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