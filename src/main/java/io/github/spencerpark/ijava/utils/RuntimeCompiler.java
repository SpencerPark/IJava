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
package io.github.spencerpark.ijava.utils;

import javax.annotation.processing.Processor;
import javax.tools.*;
import javax.tools.JavaCompiler.CompilationTask;
import java.io.File;
import java.io.FileWriter;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


public class RuntimeCompiler {
    public static Class<?> compile(String className, String content) {
        return compile(className, content, new CompileOptions(), false);
    }

    public static Class<?> compile(String className, String content, boolean forceCompile) {
        return compile(className, content, new CompileOptions(), forceCompile);
    }

    public static Class<?> compile(String className, String content, CompileOptions compileOptions, boolean forceCompile) {
        ClassLoader cl = MethodHandles.lookup().lookupClass().getClassLoader();

        try {
            Class<?> clzCompiled = cl.loadClass(className);
            System.out.printf("%s already exist! Class: %s%n", className, clzCompiled);
            if (!forceCompile) return clzCompiled;
        } catch (ClassNotFoundException ignore) {
            // ignore
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null)
            throw new RuntimeException("No compiler was provided by ToolProvider.getSystemJavaCompiler(). Make sure the jdk.compiler module is available.");

        // create source file
        File sourceFile = new File(className.replace(".", File.separator) + ".java");
        if (!sourceFile.getParentFile().exists() && !sourceFile.getParentFile().mkdirs())
            throw new RuntimeException("Cannot create parent folder: " + sourceFile.getParentFile());

        try {
            // write source file
            try (FileWriter writer = new FileWriter(sourceFile)) {
                writer.write(content);
                writer.flush();
            }
            // 1. compiler output, use System.err if null
            StringWriter out = new StringWriter();
            // 2. a diagnostic listener; if null use the compiler's default method for reporting diagnostics
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            // 3. a file manager; if null use the compiler's standard file manager
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
            // 4. compiler options, null means no options
            List<String> options = buildCompileOptions(compileOptions);
            // 5. the compilation units to compile, null means no compilation units
            Iterable<? extends JavaFileObject> compilationUnit = fileManager.getJavaFileObjectsFromFiles(Collections.singletonList(sourceFile));

            CompilationTask task = compiler.getTask(out, fileManager, diagnostics, options, null, compilationUnit);
            if (!compileOptions.processors.isEmpty()) task.setProcessors(compileOptions.processors);
            Boolean isCompileSuccess = task.call();
            fileManager.close();

            if (Boolean.FALSE.equals(isCompileSuccess)) {
                diagnostics.getDiagnostics().forEach(System.err::println);
                throw new RuntimeException("Error while compiling " + className + ", System.err for more.");
            }

            // Load compiled class
            URL[] generatedClassUrls = {new File("./").toURI().toURL()};
            try (URLClassLoader classLoader = new URLClassLoader(generatedClassUrls)) {
                return classLoader.loadClass(className);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error while compiling " + className, e);
        }
    }

    private static List<String> buildCompileOptions(CompileOptions compileOptions) throws URISyntaxException {
        List<String> options = new ArrayList<>(compileOptions.options);
        if (!options.contains("-classpath")) {
            options.add("-classpath");
            options.add(getClassPath());
        }
        return options;
    }

    public static String getClassPath() throws URISyntaxException {
        ClassLoader cl = MethodHandles.lookup().lookupClass().getClassLoader();

        StringBuilder classpath = new StringBuilder();
        String separator = System.getProperty("path.separator");
        String cp = System.getProperty("java.class.path");
        String mp = System.getProperty("jdk.module.path");

        if (cp != null && !"".equals(cp)) classpath.append(cp);
        if (mp != null && !"".equals(mp)) classpath.append(mp);

        /* [java-16] */
        // if (cl instanceof URLClassLoader) {
        //   for (URL url : ((URLClassLoader) cl).getURLs()) {
        /* [/java-16] */
        if (cl instanceof URLClassLoader urlClassLoader) {
            for (URL url : urlClassLoader.getURLs()) {
                if (classpath.length() > 0) classpath.append(separator);
                if ("file".equals(url.getProtocol())) classpath.append(new File(url.toURI()));
            }
        }
        return classpath.toString();
    }

    public static void main(String... args) {
        test();
    }

    public static void test() {
        String name = "vo.Cat";
        String clzDef = """
                package vo;

                //import lombok.*;
                   
                //@Builder
                //@Data
                public class Cat {
                    private String name;
                    private Integer age;
                }
                """;
        Class<?> clz = compile(name, clzDef);
        List<String> methods = Arrays.stream(clz.getDeclaredMethods())
                .map(method -> method.getName() + "(" + Arrays.stream(method.getGenericParameterTypes())
                        .map(type -> type.getTypeName().substring(type.getTypeName().lastIndexOf('.') + 1))
                        .collect(Collectors.joining(",")) + ")").toList();

        System.out.printf("compile done, clz: %s, clz's declared methods: %s%n", clz, methods);
    }

    public static final class CompileOptions {

        final List<? extends Processor> processors;
        final List<String> options;

        public CompileOptions() {
            this(
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }

        private CompileOptions(
                List<? extends Processor> processors,
                List<String> options
        ) {
            this.processors = processors;
            this.options = options;
        }

        public CompileOptions processors(Processor... newProcessors) {
            return processors(Arrays.asList(newProcessors));
        }

        public CompileOptions processors(List<? extends Processor> newProcessors) {
            return new CompileOptions(newProcessors, options);
        }

        public CompileOptions options(String... newOptions) {
            return options(Arrays.asList(newOptions));
        }

        public CompileOptions options(List<String> newOptions) {
            return new CompileOptions(processors, newOptions);
        }

        boolean hasOption(String opt) {
            for (String option : options)
                if (option.equalsIgnoreCase(opt))
                    return true;

            return false;
        }
    }

    // get lombok AnnotationProcessor
    //public static Processor createLombokAnnotationProcessor() {
    //    printf("----%ncreate processor%n");
    //    Processor annotationProcessor = null;
    //    ClassLoader classLoader = Lombok.class.getClassLoader();
    //    try {
    //        Class<?> aClass = classLoader.loadClass("lombok.launch.AnnotationProcessorHider");
    //        for (Class<?> declaredClass : aClass.getDeclaredClasses()) {
    //            if ("AnnotationProcessor".equals(declaredClass.getSimpleName())) {
    //                for (Constructor<?> declaredConstructor : declaredClass.getDeclaredConstructors()) {
    //                    declaredConstructor.setAccessible(true);
    //                    int parameterCount = declaredConstructor.getParameterCount();
    //                    if (parameterCount == 0) {
    //                        annotationProcessor = (Processor) declaredConstructor.newInstance();
    //                        break;
    //                    }
    //                }
    //            }
    //        }
    //        System.out.printf("found lombok annotation processor: %s%n", annotationProcessor.getClass().getCanonicalName());
    //    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
    //        throw new RuntimeException(e);
    //    }
    //    return annotationProcessor;
    //}
}

