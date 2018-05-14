package io.github.spencerpark.ijava.execution;

import jdk.jshell.EvalException;
import jdk.jshell.execution.DirectExecutionControl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * An ExecutionControl very similar to {@link jdk.jshell.execution.LocalExecutionControl} but which
 * also logs the actual result of an invocation before being serialized.
 */
public class IJavaExecutionControl extends DirectExecutionControl {
    /**
     * A special "class name" for a {@link jdk.jshell.spi.ExecutionControl.UserException} such that it may be
     * identified after serialization into an {@link jdk.jshell.EvalException} via {@link EvalException#getExceptionClassName()}.
     */
    public static final String EXECUTION_TIMEOUT_NAME = "Execution Timeout"; // Has spaces to not collide with a class name

    private static final Object UNSET = new Object();
    private static final Object EXCEPTION_OCCURRED = new Object();

    private final ExecutorService executor;

    private final long timeoutTime;
    private final TimeUnit timeoutUnit;

    private volatile Future<Object> runningTask;

    private volatile Object lastResult = UNSET;

    public IJavaExecutionControl() {
        this(-1, TimeUnit.MILLISECONDS);
    }

    public IJavaExecutionControl(long timeoutTime, TimeUnit timeoutUnit) {
        this.timeoutTime = timeoutTime;
        this.timeoutUnit = timeoutTime > 0 ? Objects.requireNonNull(timeoutUnit) : TimeUnit.MILLISECONDS;
        this.executor = Executors.newSingleThreadExecutor((r) -> new Thread(r, "IJava-executor"));
    }

    public long getTimeoutDuration() {
        return timeoutTime;
    }

    public TimeUnit getTimeoutUnit() {
        return timeoutUnit;
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
            if (this.timeoutTime > 0)
                return this.runningTask.get(this.timeoutTime, this.timeoutUnit);
            return this.runningTask.get();
        } catch (CancellationException e) {
            // If canceled this means that stop() was invoked in which case the protocol is to
            // throw an ExecutionControl.StoppedException.
            throw new StoppedException();
        } catch (ExecutionException e) {
            // The execution threw an exception. The actual exception is the cause of the ExecutionException.
            Throwable cause = e.getCause();
            if (cause instanceof InvocationTargetException) {
                // Unbox further
                cause = cause.getCause();
            }
            if (cause == null)
                throw new UserException("null", "Unknown Invocation Exception", e.getStackTrace());
            else
                throw new UserException(String.valueOf(cause.getMessage()), String.valueOf(cause.getClass().getName()), cause.getStackTrace());
        } catch (TimeoutException e) {
            this.stop();
            throw new UserException(
                    String.format("Execution timed out after configured timeout of %d %s.", this.timeoutTime, this.timeoutUnit.toString().toLowerCase()),
                    EXECUTION_TIMEOUT_NAME,
                    new StackTraceElement[]{} // The trace is irrelevant because it is in the kernel space not the user space so leave it blank.
            );
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

    @Override
    public String toString() {
        return "IJavaExecutionControl{" +
                "timeoutTime=" + timeoutTime +
                ", timeoutUnit=" + timeoutUnit +
                ", lastResult=" +
                (lastResult == UNSET ? "unset" : lastResult == EXCEPTION_OCCURRED ? "exception occurred" : String.valueOf(lastResult)) +
                '}';
    }
}
