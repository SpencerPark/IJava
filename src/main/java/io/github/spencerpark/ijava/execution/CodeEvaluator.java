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

import jdk.jshell.*;

import java.util.List;

public class CodeEvaluator {
    private static final String NO_MAGIC_RETURN = "\"__NO_MAGIC_RETURN\"";

    private final JShell shell;
    private final IJavaExecutionControlProvider executionControlProvider;
    private final String executionControlID;
    private final SourceCodeAnalysis sourceAnalyzer;

    private boolean isInitialized = false;
    private final List<String> startupScripts;

    public CodeEvaluator(JShell shell, IJavaExecutionControlProvider executionControlProvider, String executionControlID, List<String> startupScripts) {
        this.shell = shell;
        this.executionControlProvider = executionControlProvider;
        this.executionControlID = executionControlID;
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

    protected Object evalSingle(String code) throws Exception {
        IJavaExecutionControl executionControl =
                this.executionControlProvider.getRegisteredControlByID(this.executionControlID);

        List<SnippetEvent> events = this.shell.eval(code);

        Object result = null;

        // We iterate twice to make sure throwing an early exception doesn't leak the memory
        // and we `takeResult` everything.
        for (SnippetEvent event : events) {
            String key = event.value();
            if (key == null) continue;

            Snippet.SubKind subKind = event.snippet().subKind();

            // Only executable snippets make their way through the machinery we have setup in the
            // IJavaExecutionControl. Declarations for example simply take their default value without
            // being executed.
            Object value = subKind.isExecutable()
                    ? executionControl.takeResult(key)
                    : event.value();

            switch (subKind) {
                case VAR_VALUE_SUBKIND:
                case OTHER_EXPRESSION_SUBKIND:
                case TEMP_VAR_EXPRESSION_SUBKIND:
                    result = NO_MAGIC_RETURN.equals(value) ? null : value;
                    break;
                default:
                    result = null;
                    break;
            }
        }

        for (SnippetEvent event : events) {
            // If fresh snippet
            if (event.causeSnippet() == null) {
                JShellException e = event.exception();
                if (e != null) {
                    if (e instanceof EvalException && IJavaExecutionControl.EXECUTION_TIMEOUT_NAME.equals(((EvalException) e).getExceptionClassName()))
                        throw new EvaluationTimeoutException(executionControl.getTimeoutDuration(), executionControl.getTimeoutUnit(), code.trim());
                    throw e;
                }

                if (!event.status().isDefined())
                    throw new CompilationException(event);
            }
        }

        return result;
    }

    public Object eval(String code) throws Exception {
        // The init() method runs some code in the shell to initialize the environment. As such
        // it is deferred until the first user requested evaluation to cleanly return errors when
        // they happen.
        if (!this.isInitialized) {
            this.isInitialized = true;
            init();
        }

        Object lastEvalResult = null;
        SourceCodeAnalysis.CompletionInfo info;

        for (info = this.sourceAnalyzer.analyzeCompletion(code); info.completeness().isComplete(); info = analyzeCompletion(info.remaining()))
            lastEvalResult = this.evalSingle(info.source());

        if (info.completeness() != SourceCodeAnalysis.Completeness.EMPTY)
            throw new IncompleteSourceException(info.remaining().trim());

        return lastEvalResult;
    }

    public void interrupt() {
        this.shell.stop();
    }

    public void shutdown() {
        this.shell.close();
    }
}
