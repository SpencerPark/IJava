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
package io.github.spencerpark.ijava.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.spencerpark.ijava.JavaKernel;
import io.github.spencerpark.jupyter.kernel.display.DisplayData;
import io.github.spencerpark.jupyter.kernel.display.mime.MIMEType;

import java.io.Reader;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public class Display {
    private static final Gson GSON = new Gson();

    public static DisplayData render(Object o) {
        JavaKernel kernel = Kernel.getKernelInstance();

        if (kernel != null) {
            return kernel.getRenderer().render(o);
        } else {
            throw new RuntimeException("No IJava kernel running");
        }
    }

    public static DisplayData render(Object o, String... as) {
        JavaKernel kernel = Kernel.getKernelInstance();

        if (kernel != null) {
            return kernel.getRenderer().renderAs(parseAsJson(o, as), as);
        } else {
            throw new RuntimeException("No IJava kernel running");
        }
    }

    public static DisplayData renderAsJson(Object o) {
        JavaKernel kernel = Kernel.getKernelInstance();

        if (kernel != null) {
            return kernel.getRenderer().renderAs(parseAsJson(o), MIMEType.APPLICATION_JSON.toString());
        } else {
            throw new RuntimeException("No IJava kernel running");
        }
    }

    public static String display(Object o) {
        JavaKernel kernel = Kernel.getKernelInstance();

        if (kernel != null) {
            DisplayData data = kernel.getRenderer().render(o);

            String id = data.getDisplayId();
            if (id == null) {
                id = UUID.randomUUID().toString();
                data.setDisplayId(id);
            }

            kernel.display(data);

            return id;
        } else {
            throw new RuntimeException("No IJava kernel running");
        }
    }

    public static String display(Object o, String... as) {
        JavaKernel kernel = Kernel.getKernelInstance();

        if (kernel != null) {
            DisplayData data = kernel.getRenderer().renderAs(parseAsJson(o, as), as);

            String id = data.getDisplayId();
            if (id == null) {
                id = UUID.randomUUID().toString();
                data.setDisplayId(id);
            }

            kernel.display(data);

            return id;
        } else {
            throw new RuntimeException("No IJava kernel running");
        }
    }

    public static void updateDisplay(String id, Object o) {
        JavaKernel kernel = Kernel.getKernelInstance();

        if (kernel != null) {
            DisplayData data = kernel.getRenderer().render(o);
            kernel.getIO().display.updateDisplay(id, data);
        } else {
            throw new RuntimeException("No IJava kernel running");
        }
    }

    public static void updateDisplay(String id, Object o, String... as) {
        JavaKernel kernel = Kernel.getKernelInstance();

        if (kernel != null) {
            DisplayData data = kernel.getRenderer().renderAs(parseAsJson(o, as), as);
            kernel.getIO().display.updateDisplay(id, data);
        } else {
            throw new RuntimeException("No IJava kernel running");
        }
    }

    private static Object parseAsJson(Object data, String... as) {
        if (data instanceof JsonElement jsonElement) return jsonElement;
        String jsonMime = MIMEType.APPLICATION_JSON.toString();
        if (Objects.isNull(as) || as.length == 0 || Arrays.stream(as).anyMatch(mime -> mime.contains(jsonMime))) {
            if (data instanceof String jsonStr) return JsonParser.parseString(jsonStr);
            else if (data instanceof Reader reader) return JsonParser.parseReader(reader);
            else return GSON.toJsonTree(data);
        }
        return data;
    }
}
