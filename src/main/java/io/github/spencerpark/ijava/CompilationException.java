package io.github.spencerpark.ijava;

import jdk.jshell.SnippetEvent;

public class CompilationException extends Exception {
    private final SnippetEvent badSnippetCompilation;

    public CompilationException(SnippetEvent badSnippetCompilation) {
        this.badSnippetCompilation = badSnippetCompilation;
    }

    public SnippetEvent getBadSnippetCompilation() {
        return badSnippetCompilation;
    }
}
