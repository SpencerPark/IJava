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
import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;
import io.github.spencerpark.jupyter.kernel.magic.registry.LineMagic;
import io.github.spencerpark.jupyter.kernel.magic.registry.LineMagicFunction;
import io.github.spencerpark.jupyter.kernel.magic.registry.Magics;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MagicsTool {
    private static final String HIGHLIGHT_PATTERN = "\u001B[36m%s\u001B[0m";

    private CodeEvaluator evaluator;

    @LineMagic
    public void listLineMagic(List<String> args) {
        Magics magics = JavaKernel.getMagics();
        try {
            System.out.printf("registered line magics: %n\t- %s%n",
                    String.join("\n\t- ", getMagicsName(magics, "lineMagics")));
        } catch (Exception e) {
            System.out.printf("inspect line magics fail: %s%n", e.getMessage());
        }
    }

    @LineMagic
    public void listCellMagic(List<String> args) {
        Magics magics = JavaKernel.getMagics();
        try {
            System.out.printf("registered cell magics: %n\t- %s%n",
                    String.join("\n\t- ", getMagicsName(magics, "cellMagics")));
        } catch (Exception e) {
            System.out.printf("inspect cell magics fail: %s%n", e.getMessage());
        }
    }

    @LineMagic(aliases = {"list"})
    public void listMagic(List<String> args) {
        listLineMagic(Collections.emptyList());
        listCellMagic(Collections.emptyList());
    }

    @LineMagic(value = "cmd")
    public void runCommand(List<String> args) throws IOException {
        if (args.isEmpty()) return;
        Process proc = Runtime.getRuntime().exec(args.toArray(new String[0]));

        String s;
        try (InputStreamReader inputStreamReader = new InputStreamReader(proc.getInputStream());
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
            while ((s = bufferedReader.readLine()) != null) {
                System.out.println(s);
            }
        }
        try (InputStreamReader inputStreamReader = new InputStreamReader(proc.getErrorStream());
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
            while ((s = bufferedReader.readLine()) != null) {
                System.err.println(s);
            }
        }
    }

    @LineMagic(value = "read")
    public String readFromFile(List<String> args) throws IOException {
        if (args.isEmpty()) {
            System.out.println("""
                    -h/--help for help.
                    help:
                        example:
                            1. `String content = %read filename` will read file and return for content.
                    """);
            return null;
        }
        return String.join("\n", Files.readAllLines(Path.of(args.get(0))));
    }

    @LineMagic(value = "write")
    public void writeToFile(List<String> args) throws IOException {
        if (args.isEmpty()) {
            System.out.println("""
                    -h/--help for help.
                    help:
                        example:
                            1. `%write variable filename` will read variable's write to file.
                            2. `%write variable` will read variable's write to temp file.
                    """);
            return;
        }

        if (evaluator == null) getEvaluator();
        Object content;
        try {
            content = evaluator.eval(args.get(0));
        } catch (Exception e) {
            throw new RuntimeException("eval variable `" + args.get(0) + "` error, variable not found or illegal express!");
        }

        List<String> argsLast = args.size() > 1 ? Collections.singletonList(args.get(1)) : Collections.emptyList();
        writeToFile(argsLast, content.toString());
    }

    @CellMagic(value = "write")
    public void writeToFile(List<String> args, String body) throws IOException {
        String fileName = args.isEmpty()
                ? Files.createTempFile("jshell-", ".tmp").toAbsolutePath().toString()
                : args.get(0);
        File file = new File(fileName);
        if (file.getParentFile() != null && !file.getParentFile().exists() && !file.getParentFile().mkdirs())
            throw new IOException("Cannot create parent folder: " + file.getParentFile());
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(body);
            writer.flush();
        }
        System.out.printf("Write to %s success.%n", String.format(HIGHLIGHT_PATTERN, file.getAbsolutePath()));
    }

    @SuppressWarnings("unchecked")
    private Collection<String> getMagicsName(Magics magics, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        Field field = magics.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Map<String, LineMagicFunction<?>> lineMagics = (Map<String, LineMagicFunction<?>>) field.get(magics);
        return lineMagics.entrySet()
                .stream()
                .collect(Collectors.groupingBy(Map.Entry::getValue, Collectors.mapping(Map.Entry::getKey, Collectors.joining(", "))))
                .values();
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
