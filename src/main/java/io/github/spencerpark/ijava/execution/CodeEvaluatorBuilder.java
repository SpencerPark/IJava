/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 Spencer Park
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
package io.github.spencerpark.ijava.execution;

import io.github.spencerpark.jupyter.kernel.util.GlobFinder;
import jdk.jshell.JShell;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class CodeEvaluatorBuilder {
    private static final Pattern PATH_SPLITTER = Pattern.compile(File.pathSeparator, Pattern.LITERAL);
    private static final Pattern BLANK = Pattern.compile("^\\s*$");
    private static final int BUFFER_SIZE = 1024;

    private static final OutputStream STDOUT = new LazyOutputStreamDelegate(() -> System.out);
    private static final OutputStream STDERR = new LazyOutputStreamDelegate(() -> System.err);
    private static final InputStream STDIN = new LazyInputStreamDelegate(() -> System.in);

    private String timeout;
    private final List<String> classpath;
    private final List<String> compilerOpts;
    private PrintStream out;
    private PrintStream err;
    private InputStream in;
    private List<String> startupScripts;

    public CodeEvaluatorBuilder() {
        this.classpath = new LinkedList<>();
        this.compilerOpts = new LinkedList<>();
        this.startupScripts = new LinkedList<>();
    }

    public CodeEvaluatorBuilder addClasspathFromString(String classpath) {
        if (classpath == null) return this;
        if (BLANK.matcher(classpath).matches()) return this;

        Collections.addAll(this.classpath, PATH_SPLITTER.split(classpath));

        return this;
    }

    public CodeEvaluatorBuilder timeoutFromString(String timeout) {
        this.timeout = timeout;
        return this;
    }

    public CodeEvaluatorBuilder timeout(long timeout, TimeUnit timeoutUnit) {
        return this.timeoutFromString(String.format("%d %s", timeout, timeoutUnit.name()));
    }

    public CodeEvaluatorBuilder compilerOptsFromString(String opts) {
        if (opts == null) return this;
        this.compilerOpts.addAll(split(opts));
        return this;
    }

    public CodeEvaluatorBuilder compilerOpts(String... opts) {
        Collections.addAll(this.compilerOpts, opts);
        return this;
    }

    public CodeEvaluatorBuilder stdout(PrintStream out) {
        this.out = out;
        return this;
    }

    public CodeEvaluatorBuilder stderr(PrintStream err) {
        this.err = err;
        return this;
    }

    public CodeEvaluatorBuilder stdin(InputStream in) {
        this.in = in;
        return this;
    }

    public CodeEvaluatorBuilder sysStdout() {
        return this.stdout(new PrintStream(CodeEvaluatorBuilder.STDOUT));
    }

    public CodeEvaluatorBuilder sysStderr() {
        return this.stderr(new PrintStream(CodeEvaluatorBuilder.STDERR));
    }

    public CodeEvaluatorBuilder sysStdin() {
        return this.stdin(CodeEvaluatorBuilder.STDIN);
    }

    public CodeEvaluatorBuilder startupScript(String script) {
        if (script == null) return this;
        this.startupScripts.add(script);
        return this;
    }

    public CodeEvaluatorBuilder startupScript(InputStream scriptStream) {
        if (scriptStream == null) return this;

        try {
            ByteArrayOutputStream result = new ByteArrayOutputStream();

            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = scriptStream.read(buffer)) != -1)
                result.write(buffer, 0, read);

            String script = result.toString(StandardCharsets.UTF_8.name());

            this.startupScripts.add(script);
        } catch (IOException e) {
            throw new RuntimeException(String.format("IOException while reading startup script from stream: %s", e.getMessage()), e);
        } finally {
            try {
                scriptStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return this;
    }

    public CodeEvaluatorBuilder startupScriptFiles(String paths) {
        if (paths == null) return this;
        if (BLANK.matcher(paths).matches()) return this;

        for (String glob : PATH_SPLITTER.split(paths)) {
            GlobFinder resolver = new GlobFinder(glob);
            try {
                for (Path path : resolver.computeMatchingPaths())
                    this.startupScriptFile(path);
            } catch (IOException e) {
                throw new RuntimeException(String.format("IOException while computing startup scripts for '%s': %s", glob, e.getMessage()), e);
            }
        }

        return this;
    }

    public CodeEvaluatorBuilder startupScriptFile(Path path) {
        if (path == null) return this;

        if (!Files.isRegularFile(path))
            return this;

        if (!Files.isReadable(path))
            return this;

        try {
            String script = new String(Files.readAllBytes(path), "UTF-8");
            this.startupScripts.add(script);
        } catch (IOException e) {
            throw new RuntimeException(String.format("IOException while loading startup script for '%s': %s", path, e.getMessage()), e);
        }

        return this;
    }

    public CodeEvaluator build() {
        IJavaExecutionControlProvider executionControlProvider = new IJavaExecutionControlProvider();

        String executionControlID = UUID.randomUUID().toString();
        Map<String, String> executionControlParams = new LinkedHashMap<>();
        executionControlParams.put(IJavaExecutionControlProvider.REGISTRATION_ID_KEY, executionControlID);

        if (this.timeout != null)
            executionControlParams.put(IJavaExecutionControlProvider.TIMEOUT_KEY, this.timeout);

        JShell.Builder builder = JShell.builder();
        if (this.out != null) builder.out(this.out);
        if (this.err != null) builder.err(this.err);
        if (this.in != null) builder.in(this.in);

        JShell shell = builder
                .executionEngine(executionControlProvider, executionControlParams)
                .compilerOptions(this.compilerOpts.toArray(new String[0]))
                .build();

        for (String cp : this.classpath) {
            if (BLANK.matcher(cp).matches()) continue;

            GlobFinder resolver = new GlobFinder(cp);
            try {
                for (Path entry : resolver.computeMatchingPaths())
                    shell.addToClasspath(entry.toAbsolutePath().toString());
            } catch (IOException e) {
                throw new RuntimeException(String.format("IOException while computing classpath entries for '%s': %s", cp, e.getMessage()), e);
            }
        }

        return new CodeEvaluator(shell, executionControlProvider, executionControlID, this.startupScripts);
    }

    private static List<String> split(String opts) {
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

        return split;
    }
}
