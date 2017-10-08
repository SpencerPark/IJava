package io.github.spencerpark.ijava;

public class IncompleteSourceException extends Exception {
    private final String source;

    public IncompleteSourceException(String source) {
        this.source = source;
    }

    public String getSource() {
        return source;
    }
}
