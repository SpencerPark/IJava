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
package io.github.spencerpark.ijava;

import io.github.spencerpark.ijava.execution.*;
import io.github.spencerpark.ijava.magics.ClasspathMagics;
import io.github.spencerpark.ijava.magics.MavenResolver;
import io.github.spencerpark.ijava.magics.PrinterMagics;
import io.github.spencerpark.jupyter.kernel.BaseKernel;
import io.github.spencerpark.jupyter.kernel.LanguageInfo;
import io.github.spencerpark.jupyter.kernel.ReplacementOptions;
import io.github.spencerpark.jupyter.kernel.display.DisplayData;
import io.github.spencerpark.jupyter.kernel.magic.common.Load;
import io.github.spencerpark.jupyter.kernel.magic.registry.Magics;
import io.github.spencerpark.jupyter.kernel.util.CharPredicate;
import io.github.spencerpark.jupyter.kernel.util.StringStyler;
import io.github.spencerpark.jupyter.kernel.util.TextColor;
import io.github.spencerpark.jupyter.messages.Header;
import jdk.jshell.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class JavaKernel extends BaseKernel {
    public static String completeCodeSignifier() {
        return BaseKernel.IS_COMPLETE_YES;
    }

    public static String invalidCodeSignifier() {
        return BaseKernel.IS_COMPLETE_BAD;
    }

    public static String maybeCompleteCodeSignifier() {
        return BaseKernel.IS_COMPLETE_MAYBE;
    }

    private static final CharPredicate IDENTIFIER_CHAR = CharPredicate.builder()
            .inRange('a', 'z')
            .inRange('A', 'Z')
            .inRange('0', '9')
            .match('_')
            .build();
    private static final CharPredicate WS = CharPredicate.anyOf(" \t\n\r");

    private final CodeEvaluator evaluator;
    private final MavenResolver mavenResolver;

    private final MagicsSourceTransformer magicsTransformer;
    private final Magics magics;

    private final LanguageInfo languageInfo;
    private final String banner;
    private final List<LanguageInfo.Help> helpLinks;

    private final StringStyler errorStyler;

    public static boolean printWithVarName = true;
    // jupyter support ANSI_escape_code, java ansi code demo: https://stackoverflow.com/a/5762502
    private static String varNamePattern = "\u001B[36m%s\u001B[0m: ";
    private Long snippetId = 0L;

    public JavaKernel() {
        // todo for debug
        //try {
        //    System.out.println("------------- sleep start -------------");
        //    Thread.sleep(10 * 1000);
        //    System.out.println("------------- sleep end -------------");
        //} catch (InterruptedException e) {
        //    e.printStackTrace();
        //}
        this.evaluator = new CodeEvaluatorBuilder()
                .addClasspathFromString(System.getenv(IJava.CLASSPATH_KEY))
                .compilerOptsFromString(System.getenv(IJava.COMPILER_OPTS_KEY))
                .startupScript(IJava.resource(IJava.DEFAULT_SHELL_INIT_RESOURCE_PATH))
                .startupScriptFiles(System.getenv(IJava.STARTUP_SCRIPTS_KEY))
                .startupScript(System.getenv(IJava.STARTUP_SCRIPT_KEY))
                .timeoutFromString(System.getenv(IJava.TIMEOUT_DURATION_KEY))
                .sysStdout()
                .sysStderr()
                .sysStdin()
                .build();
        this.mavenResolver = new MavenResolver(this::addToClasspath);

        this.magicsTransformer = new MagicsSourceTransformer();
        this.magics = new Magics();
        this.magics.registerMagics(this.mavenResolver);
        this.magics.registerMagics(new ClasspathMagics(this::addToClasspath));
        this.magics.registerMagics(new Load(List.of(".jsh", ".jshell", ".java", ".ijava"), this::eval));
        this.magics.registerMagics(new PrinterMagics());

        this.languageInfo = new LanguageInfo.Builder("Java")
                .version(Runtime.version().toString())
                .mimetype("text/x-java-source")
                .fileExtension(".jshell")
                .pygments("java")
                .codemirror("java")
                .build();
        this.banner = String.format("Java %s :: IJava kernel %s %nProtocol v%s implementation by %s %s",
                Runtime.version().toString(),
                IJava.VERSION,
                Header.PROTOCOL_VERISON,
                KERNEL_META.getOrDefault("project", "UNKNOWN"),
                KERNEL_META.getOrDefault("version", "UNKNOWN")
        );
        this.helpLinks = List.of(
                new LanguageInfo.Help("Java tutorial", "https://docs.oracle.com/javase/tutorial/java/nutsandbolts/index.html"),
                new LanguageInfo.Help("IJava homepage", "https://github.com/SpencerPark/IJava")
        );

        this.errorStyler = new StringStyler.Builder()
                .addPrimaryStyle(TextColor.BOLD_BLACK_FG)
                .addSecondaryStyle(TextColor.BOLD_RED_FG)
                .addHighlightStyle(TextColor.BOLD_BLACK_FG)
                .addHighlightStyle(TextColor.RED_BG)
                //TODO map snippet ids to code cells and put the proper line number in the margin here
                .withLinePrefix(TextColor.BOLD_BLACK_FG + "|   ")
                .build();
    }

    public void addToClasspath(String path) {
        this.evaluator.getShell().addToClasspath(path);
    }

    public MavenResolver getMavenResolver() {
        return this.mavenResolver;
    }

    public Magics getMagics() {
        return this.magics;
    }

    @Override
    public LanguageInfo getLanguageInfo() {
        return this.languageInfo;
    }

    @Override
    public String getBanner() {
        return this.banner;
    }

    @Override
    public List<LanguageInfo.Help> getHelpLinks() {
        return this.helpLinks;
    }

    @Override
    public List<String> formatError(Exception e) {
        if (e instanceof CompilationException compilationexception) {
            return formatCompilationException(compilationexception);
        } else if (e instanceof IncompleteSourceException incompleteSourceException) {
            return formatIncompleteSourceException(incompleteSourceException);
        } else if (e instanceof EvalException evalException) {
            return formatEvalException(evalException);
        } else if (e instanceof UnresolvedReferenceException unresolvedReferenceException) {
            return formatUnresolvedReferenceException(unresolvedReferenceException);
        } else if (e instanceof EvaluationTimeoutException evaluationTimeoutException) {
            return formatEvaluationTimeoutException(evaluationTimeoutException);
        } else if (e instanceof EvaluationInterruptedException evaluationInterruptedException) {
            return formatEvaluationInterruptedException(evaluationInterruptedException);
        } else {
            return new LinkedList<>(super.formatError(e));
        }
    }

    private List<String> formatCompilationException(CompilationException e) {
        List<String> fmt = new ArrayList<>();
        SnippetEvent event = e.getBadSnippetCompilation();
        Snippet snippet = event.snippet();
        this.evaluator.getShell().diagnostics(snippet)
                .forEach(d -> {
                    // If has line information related, highlight that span
                    if (d.getStartPosition() >= 0 && d.getEndPosition() >= 0)
                        fmt.addAll(this.errorStyler.highlightSubstringLines(snippet.source(),
                                (int) d.getStartPosition(), (int) d.getEndPosition()));
                    else
                        fmt.addAll(this.errorStyler.primaryLines(snippet.source()));

                    // Add the error message
                    for (String line : StringStyler.splitLines(d.getMessage(null))) {
                        // Skip the information about the location of the error as it is highlighted instead
                        if (!line.trim().startsWith("location:"))
                            fmt.add(this.errorStyler.secondary(line));
                    }

                    fmt.add(""); // Add a blank line
                });
        if (snippet instanceof DeclarationSnippet declarationSnippet) {
            formatUnresolvedDep(declarationSnippet, fmt);
        }

        return fmt;
    }

    private List<String> formatIncompleteSourceException(IncompleteSourceException e) {
        List<String> fmt = new ArrayList<>();

        String source = e.getSource();
        fmt.add(this.errorStyler.secondary("Incomplete input:"));
        fmt.addAll(this.errorStyler.primaryLines(source));

        return fmt;
    }

    private List<String> formatEvalException(EvalException e) {
        List<String> fmt = new ArrayList<>();

        String evalExceptionClassName = EvalException.class.getName();
        String actualExceptionName = e.getExceptionClassName();
        super.formatError(e).stream()
                .map(line -> line.replace(evalExceptionClassName, actualExceptionName))
                .forEach(fmt::add);

        return fmt;
    }

    private List<String> formatUnresolvedReferenceException(UnresolvedReferenceException e) {
        List<String> fmt = new ArrayList<>();
        formatUnresolvedDep(e.getSnippet(), fmt);
        return fmt;
    }

    private void formatUnresolvedDep(DeclarationSnippet declarationSnippet, final List<String> fmt) {
        List<String> unresolvedDependencies = this.evaluator.getShell().unresolvedDependencies(declarationSnippet).toList();
        if (!unresolvedDependencies.isEmpty()) {
            fmt.addAll(this.errorStyler.primaryLines(declarationSnippet.source()));
            fmt.add(this.errorStyler.secondary("Unresolved dependencies:"));
            unresolvedDependencies.forEach(dep -> fmt.add(this.errorStyler.secondary("   - " + dep)));
        }
    }

    private List<String> formatEvaluationTimeoutException(EvaluationTimeoutException e) {
        List<String> fmt = new ArrayList<>(this.errorStyler.primaryLines(e.getSource()));

        fmt.add(this.errorStyler.secondary(String.format(
                "Evaluation timed out after %d %s.",
                e.getDuration(),
                e.getUnit().name().toLowerCase())
        ));

        return fmt;
    }

    private List<String> formatEvaluationInterruptedException(EvaluationInterruptedException e) {
        List<String> fmt = new ArrayList<>(this.errorStyler.primaryLines(e.getSource()));

        fmt.add(this.errorStyler.secondary("Evaluation interrupted."));

        return fmt;
    }

    public Object evalRaw(String expr) throws Exception {
        expr = this.magicsTransformer.transformMagics(expr);

        return this.evaluator.eval(expr);
    }

    @Override
    public DisplayData eval(String expr) throws Exception {
        Object result = this.evalRaw(expr);

        // last snippet is ExpressSnippet or VarSnippet -> getSource().replaceAll("\s+", "")
        if (result == null) return null;
        if (result instanceof DisplayData displayData) return displayData;

        if (printWithVarName) {
            Optional<Snippet> lastSnippet = this.evaluator.getShell().snippets().skip(snippetId).reduce((first, second) -> second);
            if (lastSnippet.isPresent()) {
                Snippet snippet = lastSnippet.get();
                if (snippet instanceof ExpressionSnippet || snippet instanceof VarSnippet) {
                    snippetId = snippet.id().matches("\\d+") ? (Long.parseLong(snippet.id()) - 1) : (snippetId + 1);
                    String sourceStr = snippet.source().replaceAll("\\s+", "");
                    if (sourceStr.length() > 32) sourceStr = sourceStr.substring(0, 32) + "...";
                    return this.getRenderer().render(String.format(varNamePattern, sourceStr) + result);
                }
            }
        }

        return this.getRenderer().render(result);
    }

    @Override
    public DisplayData inspect(String code, int at, boolean extraDetail) {
        // Move the code position to the end of the identifier to make the inspection work at any
        // point in the identifier. i.e "System.o|ut" or "System.out|" will return the same result.
        while (at + 1 < code.length() && IDENTIFIER_CHAR.test(code.charAt(at + 1))) at++;

        // If the next non-whitespace character is an opening paren '(' then this must be included
        // in the documentation search to ensure it searches for a method call.
        int parenIdx = at;
        while (parenIdx + 1 < code.length() && WS.test(code.charAt(parenIdx + 1))) parenIdx++;
        if (parenIdx + 1 < code.length() && code.charAt(parenIdx + 1) == '(') at = parenIdx + 1;

        List<SourceCodeAnalysis.Documentation> documentations = this.evaluator.getShell().sourceCodeAnalysis().documentation(code, at + 1, true);
        if (documentations == null || documentations.isEmpty()) {
            return null;
        }

        DisplayData fmtDocs = new DisplayData(
                documentations.stream()
                        .map(doc -> {
                            String formatted = doc.signature();

                            String javadoc = doc.javadoc();
                            if (javadoc != null) formatted += '\n' + javadoc;

                            return formatted;
                        }).collect(Collectors.joining("\n\n"))
        );

        fmtDocs.putHTML(
                documentations.stream()
                        .map(doc -> {
                            String formatted = doc.signature();

                            // TODO consider compiling the javadoc to html for pretty printing
                            String javadoc = doc.javadoc();
                            if (javadoc != null) formatted += "<br/>" + javadoc;

                            return formatted;
                        }).collect(Collectors.joining("<br/><br/>"))
        );

        return fmtDocs;
    }

    @Override
    public ReplacementOptions complete(String code, int at) {
        int[] replaceStart = new int[1]; // As of now this is always the same as the cursor...
        List<SourceCodeAnalysis.Suggestion> suggestions = this.evaluator.getShell().sourceCodeAnalysis().completionSuggestions(code, at, replaceStart);
        if (suggestions == null || suggestions.isEmpty()) return null;

        //      .sorted((s1, s2) -> s1.matchesType() ? s2.matchesType() ? 0 : -1 : s2.matchesType() ? 1 : 0)
        List<String> options = suggestions.stream()
                .sorted((s1, s2) -> (s1.matchesType() ? 0 : 1) + (s2.matchesType() ? 0 : -1))
                .map(SourceCodeAnalysis.Suggestion::continuation)
                .distinct()
                .toList();

        return new ReplacementOptions(options, replaceStart[0], at);
    }

    @Override
    public String isComplete(String code) {
        return this.evaluator.isComplete(code);
    }

    @Override
    public void onShutdown(boolean isRestarting) {
        this.evaluator.shutdown();
    }

    @Override
    public void interrupt() {
        this.evaluator.interrupt();
    }
}
