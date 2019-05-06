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
package io.github.spencerpark.ijava;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.spencerpark.jupyter.channels.JupyterConnection;
import io.github.spencerpark.jupyter.channels.JupyterSocket;
import io.github.spencerpark.jupyter.kernel.KernelConnectionProperties;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;

public class IJava {
    public static final String COMPILER_OPTS_KEY = "IJAVA_COMPILER_OPTS";
    public static final String TIMEOUT_DURATION_KEY = "IJAVA_TIMEOUT";
    public static final String CLASSPATH_KEY = "IJAVA_CLASSPATH";
    public static final String STARTUP_SCRIPTS_KEY = "IJAVA_STARTUP_SCRIPTS_PATH";
    public static final String STARTUP_SCRIPT_KEY = "IJAVA_STARTUP_SCRIPT";

    public static final String DEFAULT_SHELL_INIT_RESOURCE_PATH = "ijava-jshell-init.jshell";

    public static final String VERSION;

    public static InputStream resource(String path) {
        return IJava.class.getClassLoader().getResourceAsStream(path);
    }

    static {
        InputStream metaStream = resource("ijava-kernel-metadata.json");
        Reader metaReader = new InputStreamReader(metaStream);
        try {
            JsonElement meta = new JsonParser().parse(metaReader);
            VERSION = meta.getAsJsonObject().get("version").getAsString();
        } finally {
            try {
                metaReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static JavaKernel kernel = null;

    /**
     * Obtain a reference to the kernel created by running {@link #main(String[])}. This
     * kernel may be null if one is not present but as the main use for this method is
     * for the kernel user code to access kernel services.
     *
     * @return the kernel created by running {@link #main(String[])} or {@code null} if
     *         one has not yet (or already created and finished) been created.
     */
    public static JavaKernel getKernelInstance() {
        return IJava.kernel;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1)
            throw new IllegalArgumentException("Missing connection file argument");

        Path connectionFile = Paths.get(args[0]);

        if (!Files.isRegularFile(connectionFile))
            throw new IllegalArgumentException("Connection file '" + connectionFile + "' isn't a file.");

        String contents = new String(Files.readAllBytes(connectionFile));

        JupyterSocket.JUPYTER_LOGGER.setLevel(Level.WARNING);

        KernelConnectionProperties connProps = KernelConnectionProperties.parse(contents);
        JupyterConnection connection = new JupyterConnection(connProps);

        kernel = new JavaKernel();
        kernel.becomeHandlerForConnection(connection);

        connection.connect();
        connection.waitUntilClose();

        kernel = null;

        System.exit(0);
    }
}
