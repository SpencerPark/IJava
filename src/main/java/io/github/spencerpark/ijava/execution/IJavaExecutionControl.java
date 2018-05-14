package io.github.spencerpark.ijava.execution;

import jdk.jshell.execution.DirectExecutionControl;

import java.lang.reflect.Method;
import java.util.concurrent.*;

/**
 * An ExecutionControl very similar to {@link jdk.jshell.execution.LocalExecutionControl} but which
 * also logs the actual result of an invocation before being serialized.
 */
public class IJavaExecutionControl extends DirectExecutionControl {
    private static final Object UNSET = new Object();
    private static final Object EXCEPTION_OCCURRED = new Object();

    private final ExecutorService executor;

    private final TimeUnit timeoutUnit;
    private final long timeoutTime;

    private volatile Future<Object> runningTask;

    private volatile Object lastResult = UNSET;

    public IJavaExecutionControl() {
        this(null, -1);
    }

    public IJavaExecutionControl(TimeUnit timeoutUnit, long timeoutTime) {
        this.timeoutUnit = timeoutUnit;
        this.timeoutTime = timeoutTime;
        this.executor = Executors.newSingleThreadExecutor((r) -> new Thread(r, "IJava-executor"));
    }

    public Object getLastResult() {
        Object lastResult = this.lastResult;
        if (lastResult == UNSET)
            throw new IllegalStateException("Nothing has been executed yet.");
        else if (lastResult == EXCEPTION_OCCURRED)
            throw new IllegalStateException("Last execution resulted in an exception.");
        return lastResult;
    }

    public Object getLastResultOrDefault(Object def) {
        Object lastResult = this.lastResult;
        if (lastResult == UNSET || lastResult == EXCEPTION_OCCURRED)
            return def;
        return lastResult;
    }

    /**
     * @param doitMethod
     *
     * @return
     *
     * @throws TimeoutException
     * @throws Exception
     */
    private Object execute(Method doitMethod) throws TimeoutException, Exception {
        this.runningTask = this.executor.submit(() -> doitMethod.invoke(null));

        try {
            if (this.timeoutUnit != null)
                return this.runningTask.get(this.timeoutTime, this.timeoutUnit);
            return this.runningTask.get();
        } catch (CancellationException e) {
            // If canceled the return value is not necessary neither is throwing anything.
            return "Snippet canceled!";
        } catch (ExecutionException e) {
            // The execution threw an exception. The actual exception is the cause of the ExecutionException.
            Throwable cause = e.getCause();
            if (cause instanceof Exception)
                throw (Exception) cause;
            else
                throw new RuntimeException(cause);
        }
    }

    @Override
    protected String invoke(Method doitMethod) throws Exception {
        try {
            Object value = this.execute(doitMethod);
            this.lastResult = value;
            return valueString(value);
        } catch (Exception e) {
            this.lastResult = EXCEPTION_OCCURRED;
            throw e;
        }
    }

    @Override
    public void stop() throws EngineTerminationException, InternalException {
        if (this.runningTask != null)
            this.runningTask.cancel(true);
    }
}
