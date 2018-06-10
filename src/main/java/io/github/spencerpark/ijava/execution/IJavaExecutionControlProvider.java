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
