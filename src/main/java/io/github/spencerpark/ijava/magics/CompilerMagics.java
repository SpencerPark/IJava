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
package io.github.spencerpark.ijava.magics;

import io.github.spencerpark.ijava.IJava;
import io.github.spencerpark.ijava.JavaKernel;
import io.github.spencerpark.ijava.execution.CodeEvaluator;
import io.github.spencerpark.ijava.utils.RuntimeCompiler;
import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;
import io.github.spencerpark.jupyter.kernel.util.GlobFinder;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CompilerMagics {
    private static final List<String> COMMENT_PATTERNS = List.of("/\\*(.|\\s)*?\\*/", "//.*\\n*");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("\\s*package\\s+(?<package>\\w+(\\.\\w+){0,100})\\s*");

    private final Consumer<String> addToClasspath;

    private CodeEvaluator evaluator;

    public CompilerMagics(Consumer<String> addToClasspath) {
        this.addToClasspath = addToClasspath;
    }

    @CellMagic(aliases = {"compile"})
    public void compile(List<String> args, String body) {
        if (args.isEmpty()) throw new RuntimeException("Please specify *Class Canonical Name* in args!");

        // 1. autofill package base on class canonical name
        String bodyCopy = body;
        for (String pattern : COMMENT_PATTERNS) bodyCopy = bodyCopy.replaceAll(pattern, "");
        Matcher matcher = PACKAGE_PATTERN.matcher(bodyCopy);
        String clzCanonicalName = args.get(0);
        String[] namePart = clzCanonicalName.split("\\.");
        if (!matcher.find()) body = String.format("package %s;", namePart[namePart.length - 1]) + body;

        // 2. build
        RuntimeCompiler.compile(clzCanonicalName, body, buildCompilerOptions(), true);

        // 3. add to classpath
        // todo hot-reload class
        GlobFinder resolver = new GlobFinder(namePart[0]);
        try {
            resolver.computeMatchingPaths().forEach(path -> this.addToClasspath.accept(path.getParent().toAbsolutePath().toString()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public RuntimeCompiler.CompileOptions buildCompilerOptions() {
        RuntimeCompiler.CompileOptions compileOptions = new RuntimeCompiler.CompileOptions();
        try {
            if (evaluator == null) getEvaluator();
            Object result = evaluator.eval("""
                    import java.lang.invoke.MethodHandles;

                    ClassLoader cl = MethodHandles.lookup().lookupClass().getClassLoader();

                    StringBuilder classpath = new StringBuilder();
                    String separator = System.getProperty("path.separator");
                    String cp = System.getProperty("java.class.path");
                    String mp = System.getProperty("jdk.module.path");

                    if (cp != null && !"".equals(cp)) classpath.append(cp);
                    if (mp != null && !"".equals(mp)) classpath.append(mp);

                    if (cl instanceof URLClassLoader) {
                       for (URL url : ((URLClassLoader) cl).getURLs()) {
                            if (classpath.length() > 0) classpath.append(separator);
                            if ("file".equals(url.getProtocol())) classpath.append(new File(url.toURI()));
                        }
                    }
                    classpath.toString()
                    """);
            return compileOptions.options("-classpath", (String) result);
        } catch (Exception e) {
            System.err.println("get jshell instance class path error. keep default class path.");
        }
        return compileOptions;
    }

    public void getEvaluator() {
        try {
            JavaKernel kernel = IJava.getKernelInstance();
            Field field = kernel.getClass().getDeclaredField("evaluator");
            field.setAccessible(true);
            evaluator = (CodeEvaluator) field.get(kernel);
        } catch (Exception e) {
            throw new RuntimeException("Compiler get JShell evaluator instance error." + e.getMessage());
        }
    }
}
