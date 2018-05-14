package io.github.spencerpark.ijava.execution;

import jdk.jshell.spi.ExecutionControl;
import jdk.jshell.spi.ExecutionControlProvider;
import jdk.jshell.spi.ExecutionEnv;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

public class IJavaExecutionControlProvider implements ExecutionControlProvider {
    /**
     * The parameter key that when given causes the generated control to be registered
     * for later reference by the parameter value.
     */
    public static final String REGISTRATION_ID_KEY = "registration-id";

    public static final String TIMEOUT_KEY = "timeout";

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
        long timeout = Long.parseLong(parameters.getOrDefault(TIMEOUT_KEY, "-1"));

        IJavaExecutionControl control = timeout > 0
                ? new IJavaExecutionControl(TimeUnit.MILLISECONDS, timeout)
                : new IJavaExecutionControl();

        String id = parameters.get(REGISTRATION_ID_KEY);
        if (id != null)
            this.controllers.put(id, control);

        return control;
    }
}
