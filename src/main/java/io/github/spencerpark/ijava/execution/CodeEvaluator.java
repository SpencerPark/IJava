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
package io.github.spencerpark.ijava.execution;

import io.github.spencerpark.jupyter.messages.MIMEBundle;
import jdk.jshell.JShell;
import jdk.jshell.JShellException;
import jdk.jshell.SnippetEvent;
import jdk.jshell.SourceCodeAnalysis;

import java.util.List;

public class CodeEvaluator {
    private static final String NO_MAGIC_RETURN = "\"__NO_MAGIC_RETURN\"";

    private final JShell shell;
    private final SourceCodeAnalysis sourceAnalyzer;

    private boolean isInitialized = false;
    private final List<String> startupScripts;

    public CodeEvaluator(JShell shell, List<String> startupScripts) {
        this.shell = shell;
        this.sourceAnalyzer = this.shell.sourceCodeAnalysis();
        this.startupScripts = startupScripts;
    }

    public JShell getShell() {
        return this.shell;
    }

    private SourceCodeAnalysis.CompletionInfo analyzeCompletion(String source) {
        return this.sourceAnalyzer.analyzeCompletion(source);
    }

    private void init() throws Exception {
        for (String script : this.startupScripts)
            eval(script);

        this.startupScripts.clear();
    }

    protected String evalSingle(String code) throws Exception {
        String result = null;

        for (SnippetEvent event : this.shell.eval(code)) {
            // If fresh snippet
            if (event.causeSnippet() == null) {
                JShellException e = event.exception();
                if (e != null) throw e;

                if (!event.status().isDefined())
                    throw new CompilationException(event);

                switch (event.snippet().subKind()) {
                    case VAR_VALUE_SUBKIND:
                    case OTHER_EXPRESSION_SUBKIND:
                    case TEMP_VAR_EXPRESSION_SUBKIND:
                        result = event.value();
                        if (NO_MAGIC_RETURN.equals(result))
                            result = null;

                        break;
                    default:
                        result = null;
                        break;
                }
            }
        }

        return result;
    }

    public MIMEBundle eval(String code) throws Exception {
        // The init() method runs some code in the shell to initialize the environment. As such
        // it is deferred until the first user requested evaluation to cleanly return errors when
        // they happen.
        if (!this.isInitialized) {
            this.isInitialized = true;
            init();
        }

        String lastEvalResult = null;
        SourceCodeAnalysis.CompletionInfo info;

        for (info = this.sourceAnalyzer.analyzeCompletion(code); info.completeness().isComplete(); info = analyzeCompletion(info.remaining()))
            lastEvalResult = this.evalSingle(info.source());

        if (info.completeness() != SourceCodeAnalysis.Completeness.EMPTY)
            throw new IncompleteSourceException(info.remaining().trim());

        return lastEvalResult == null || lastEvalResult.isEmpty() ? null : new MIMEBundle(lastEvalResult);
    }

    public void interrupt() {
        this.shell.stop();
    }

    public void shutdown() {
        this.shell.close();
    }
}
