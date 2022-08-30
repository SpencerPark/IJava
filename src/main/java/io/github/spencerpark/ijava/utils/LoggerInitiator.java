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
package io.github.spencerpark.ijava.utils;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.OutputStreamAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.OutputStream;
import java.text.SimpleDateFormat;

import static org.apache.logging.log4j.LogManager.getLogger;

public class LoggerInitiator {
    private static volatile boolean initialized = false;

    static {
        init();
    }

    private LoggerInitiator() {
    }

    public static void init() {
        // config doc https://logging.apache.org/log4j/2.x/manual/configuration.html
        // demo from https://logging.apache.org/log4j/2.x/manual/customconfig.html
        if (initialized) return;
        String pattern = "%d{yyyy-MM-dd} %d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n";

        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setStatusLevel(Level.WARN);
        builder.setConfigurationName("Jshell-Logger");

        // configure a console appender
        builder.add(
                builder.newAppender("Stdout", "CONSOLE")
                        .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT)
                        .add(builder.newLayout("PatternLayout").addAttribute("pattern", pattern))
        );

        String fileParent = System.getProperty("os.name").toLowerCase().contains("win")
                ? System.getProperty("java.io.tmpdir")
                : "/var";
        String fileName = String.format("%s/log/jshell/jshell-%s.log", fileParent, new SimpleDateFormat("yyyyMMdd_HHmmss.SSS").format(System.currentTimeMillis()));
        builder.add(
                builder.newAppender("File", "FILE")
                        .addAttribute("fileName", fileName)
                        .add(builder.newLayout("PatternLayout").addAttribute("pattern", pattern))
        );

        // configure the root logger
        builder.add(builder.newRootLogger(Level.ALL).add(builder.newAppenderRef("Stdout")).add(builder.newAppenderRef("File")));
        BuiltConfiguration config = builder.build();
        Configurator.initialize(config);
        //addAppender(config, System.out, "Print", pattern);

        Logger test = test();
        initialized = true;
        test.info("log to file: {}", fileName);
    }

    public static void addAppender(final Configuration config, final OutputStream outputStream, final String outputStreamName, String pattern) {
        // final Configuration config = LoggerContext.getContext(false).getConfiguration();
        final PatternLayout layout = PatternLayout.newBuilder().withConfiguration(config).withPattern(pattern).build();
        final Appender appender = OutputStreamAppender.newBuilder().setLayout(layout).setTarget(outputStream).setName(outputStreamName).build();
        appender.start();
        config.addAppender(appender);

        // update Loggers. root logger already in getLoggers()'s map, getRootLogger.addAppender is unnecessary
        config.getLoggers().values().forEach(loggerConfig -> loggerConfig.addAppender(appender, null, null));
    }

    public static Logger test() {
        Logger logger = getLogger("test-jshell-log");
        logger.debug("--------------- test-jshell-log debug ------------------");
        logger.error("--------------- test-jshell-log error ------------------");
        return logger;
    }
}
