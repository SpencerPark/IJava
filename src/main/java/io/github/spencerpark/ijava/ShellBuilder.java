/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Spencer Park
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
package io.github.spencerpark.ijava;

import jdk.jshell.JShell;

import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ShellBuilder {
    public static final String VM_OPTS_KEY = "IJAVA_VM_OPTS";
    public static final String COMPILER_OPTS_KEY = "IJAVA_COMPILER_OPTS";

    private static final OutputStream stdout = new LazyOutputStreamDelegate(() -> System.out);
    private static final OutputStream stderr = new LazyOutputStreamDelegate(() -> System.err);

    public static JShell create(boolean addCurrentToClasspath) {
        return create(addCurrentToClasspath, Collections.emptyMap());
    }

    public static JShell create(boolean addCurrentToClasspath, Map<String, String> envDefaults) {
        String vmOpts = System.getenv(VM_OPTS_KEY);
        if (vmOpts == null) vmOpts = envDefaults.getOrDefault(VM_OPTS_KEY, "");

        String compilerOpts = System.getenv(COMPILER_OPTS_KEY);
        if (compilerOpts == null) compilerOpts = envDefaults.getOrDefault(COMPILER_OPTS_KEY, "");

        JShell shell = JShell.builder()
                .remoteVMOptions(split(vmOpts))
                .compilerOptions(split(compilerOpts))
                .out(new PrintStream(stdout))
                .err(new PrintStream(stderr))
                .build();

        if (addCurrentToClasspath) {
            try {
                addCurrentToClasspath(shell);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }

        return shell;
    }

    private static void addCurrentToClasspath(JShell shell) throws URISyntaxException {
        shell.addToClasspath(ShellBuilder.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
    }

    private static String[] split(String opts) {
        opts = opts.trim();

        List<String> split = new LinkedList<>();

        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escape = false;
        for (char c : opts.toCharArray()) {
            switch (c) {
                case ' ':
                case '\t':
                    if (inQuotes) {
                        current.append(c);
                    } else if (current.length() > 0) {
                        // If whitespace is closing the string the add the current and reset
                        split.add(current.toString());
                        current.setLength(0);
                    }
                    break;
                case '\\':
                    if (escape) {
                        current.append("\\\\");
                        escape = false;
                    } else {
                        escape = true;
                    }
                    break;
                case '\"':
                    if (escape) {
                        current.append('"');
                        escape = false;
                    } else {
                        if (current.length() > 0 && inQuotes) {
                            split.add(current.toString());
                            current.setLength(0);
                            inQuotes = false;
                        } else {
                            inQuotes = true;
                        }
                    }
                    break;
                default:
                    current.append(c);
            }
        }

        if (current.length() > 0) {
            split.add(current.toString());
        }

        return split.toArray(new String[split.size()]);
    }
}
