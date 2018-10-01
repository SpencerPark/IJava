package io.github.spencerpark.ijava.execution;

public class EvaluationInterruptedException extends Exception {
    private final String source;

    public EvaluationInterruptedException(String source) {
        this.source = source;
    }

    public String getSource() {
        return source;
    }

    @Override
    public String getMessage() {
        return String.format("Evaluator was interrupted while executing: '%s'",
                this.source);
    }
}
