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

import io.github.spencerpark.ijava.JavaKernel;
import jdk.jshell.*;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeEvaluator {
    private static final Pattern WHITESPACE_PREFIX = Pattern.compile("(?:^|\r?\n)(?<ws>\\s*).*$");
    private static final Pattern LAST_LINE = Pattern.compile("(?:^|\r?\n)(?<last>.*)$");

    private static final String NO_MAGIC_RETURN = "\"__NO_MAGIC_RETURN\"";

    private final JShell shell;
    private final IJavaExecutionControlProvider executionControlProvider;
    private final String executionControlID;
    private final SourceCodeAnalysis sourceAnalyzer;

    private boolean isInitialized = false;
    private final List<String> startupScripts;

    private final String indentation = "  ";

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
                    if (e instanceof EvalException) {
                        switch (((EvalException) e).getExceptionClassName()) {
                            case IJavaExecutionControl.EXECUTION_TIMEOUT_NAME:
                                throw new EvaluationTimeoutException(executionControl.getTimeoutDuration(), executionControl.getTimeoutUnit(), code.trim());
                            case IJavaExecutionControl.EXECUTION_INTERRUPTED_NAME:
                                throw new EvaluationInterruptedException(code.trim());
                            default:
                                throw e;
                        }
                    }

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

    private String computeIndentation(String partialStatement) {
        // Find the indentation of the last line
        Matcher m = WHITESPACE_PREFIX.matcher(partialStatement);
        String currentIndentation = m.find() ? m.group("ws") : "";

        m = LAST_LINE.matcher(partialStatement);
        if (!m.find())
            throw new Error("Pattern broken. Every string should have a last line.");

        // If a brace or paren was opened on the last line and not closed, indent some more.
        String lastLine = m.group("last");
        int newlyOpenedBraces = -1;
        int newlyOpenedParens = -1;
        for (int i = 0; i < lastLine.length(); i++) {
            switch (lastLine.charAt(i)) {
                case '}':
                    // Ignore closing if one has not been opened on this line yet
                    if (newlyOpenedBraces == -1) continue;
                    // Otherwise close an opened one from this line
                    newlyOpenedBraces--;
                    break;
                case ')':
                    // Same as for braces, but with the parens
                    if (newlyOpenedParens == -1) continue;
                    newlyOpenedParens--;
                    break;
                case '{':
                    // A brace was opened on this line!
                    // If the first then get out og the -1 special case with an extra addition
                    if (newlyOpenedBraces == -1) newlyOpenedBraces++;
                    newlyOpenedBraces++;
                    break;
                case '(':
                    if (newlyOpenedParens == -1) newlyOpenedParens++;
                    newlyOpenedParens++;
                    break;
            }
        }

        return newlyOpenedBraces > 0 || newlyOpenedParens > 0
                ? currentIndentation + this.indentation
                : currentIndentation;
    }

    public String isComplete(String code) {
        SourceCodeAnalysis.CompletionInfo info = this.sourceAnalyzer.analyzeCompletion(code);
        while (info.completeness().isComplete())
            info = analyzeCompletion(info.remaining());

        switch (info.completeness()) {
            case UNKNOWN:
                // Unknown means "bad code" and the only way to see if is complete is
                // to execute it.
                return JavaKernel.invalidCodeSignifier();
            case COMPLETE:
            case COMPLETE_WITH_SEMI:
            case EMPTY:
                return JavaKernel.completeCodeSignifier();
            case CONSIDERED_INCOMPLETE:
            case DEFINITELY_INCOMPLETE:
                // Compute the indent of the last line and match it
                return this.computeIndentation(info.remaining());
            default:
                // For completeness, return an "I don't know" if we somehow get down here
                return JavaKernel.maybeCompleteCodeSignifier();
        }
    }

    public void interrupt() {
        IJavaExecutionControl executionControl =
                this.executionControlProvider.getRegisteredControlByID(this.executionControlID);

        if (executionControl != null)
            executionControl.interrupt();
    }

    public void shutdown() {
        this.shell.close();
    }
}
