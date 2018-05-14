package io.github.spencerpark.ijava.execution;

import jdk.jshell.spi.ExecutionControl;
import jdk.jshell.spi.ExecutionControlProvider;
import jdk.jshell.spi.ExecutionEnv;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IJavaExecutionControlProvider implements ExecutionControlProvider {
    /**
     * The parameter key that when given causes the generated control to be registered
     * for later reference by the parameter value.
     */
    public static final String REGISTRATION_ID_KEY = "registration-id";

    /**
     * The parameter key that when given is parsed as a timeout value for a single statement
     * execution. If just a number then the value is assumed to be in milliseconds, otherwise
     * the text following the number is "parsed" with {@link TimeUnit#valueOf(String)}
     */
    public static final String TIMEOUT_KEY = "timeout";

    private static final Pattern TIMEOUT_PATTERN = Pattern.compile("^(?<dur>-?\\d+)\\W*(?<unit>[A-Za-z]+)?$");

    private final Map<String, IJavaExecutionControl> controllers = new WeakHashMap<>();

    public IJavaExecutionControl getRegisteredControlByID(String id) {
        return this.controllers.get(id);
    }

    @Override
    public String name() {
        return "IJava";
    }

    @Override
    public ExecutionControl generate(ExecutionEnv env, Map<String, String> parameters) throws Throwable {
        long timeout = -1;
        TimeUnit timeUnit = TimeUnit.MILLISECONDS;

        String timeoutRaw = parameters.get(TIMEOUT_KEY);
        if (timeoutRaw != null) {
            Matcher m = TIMEOUT_PATTERN.matcher(timeoutRaw);
            if (!m.matches())
                throw new IllegalArgumentException("Invalid timeout string: " + timeoutRaw);

            timeout = Long.parseLong(m.group("dur"));

            if (m.group("unit") != null) {
                try {
                    timeUnit = TimeUnit.valueOf(m.group("unit").toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid timeout unit: " + m.group("unit"));
                }
            }
        }

        IJavaExecutionControl control = timeout > 0
                ? new IJavaExecutionControl(timeout, timeUnit)
                : new IJavaExecutionControl();

        String id = parameters.get(REGISTRATION_ID_KEY);
        if (id != null)
            this.controllers.put(id, control);

        return control;
    }
}
