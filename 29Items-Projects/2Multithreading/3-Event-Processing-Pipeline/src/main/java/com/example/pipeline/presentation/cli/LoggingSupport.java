package com.example.pipeline.presentation.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Configures {@code java.util.logging} at startup.
 *
 * <p><strong>Why {@code java.util.logging} and not SLF4J + Logback.</strong> The project
 * commits to zero runtime dependencies (see {@code docs/ARCHITECTURE.md} §2.6). A logging
 * facade plus a backend is two jars and a configuration format for what this application
 * needs: levelled messages on stderr with thread names. {@code java.util.logging} is
 * verbose to configure and its default format is ugly, both of which this class fixes once —
 * a worse API in one place beats a dependency everywhere.
 *
 * <p><strong>Thread names in every line are the point.</strong> In a pipeline the first
 * question about any log line is which stage emitted it, which is why
 * {@code src/main/resources/logging.properties} installs {@link PipelineLogFormatter}
 * instead of the JDK's {@code SimpleFormatter} — no pattern string can print a thread
 * name, because a {@code LogRecord} carries only a thread id.
 *
 * <p>Precedence: an explicit {@code -Djava.util.logging.config.file} always wins — an
 * operator debugging a run must be able to override the packaged configuration without
 * rebuilding. Otherwise the bundled {@code logging.properties} is read from the classpath
 * and the configured level applied on top.
 */
public final class LoggingSupport {

    /** Classpath resource holding the packaged logging configuration. */
    public static final String CONFIG_RESOURCE = "logging.properties";

    /** System property the JDK itself honours; when set, this class does not interfere. */
    public static final String JDK_CONFIG_PROPERTY = "java.util.logging.config.file";

    /** Root logger name — the empty string, as the JDK defines it. */
    private static final String ROOT_LOGGER = "";

    private LoggingSupport() {
    }

    /**
     * Applies the packaged configuration and then the requested level.
     *
     * <p>Never throws: logging that cannot be configured must not prevent a run. A failure
     * degrades to the JDK defaults and says so on stderr, because a silent fallback would
     * make a later "why is nothing logged at FINE?" impossible to answer.
     *
     * @param levelName a {@code java.util.logging} level name, e.g. {@code INFO} or {@code FINE}
     */
    public static void configure(String levelName) {
        if (System.getProperty(JDK_CONFIG_PROPERTY) != null) {
            // The operator asked for a specific file; honour it untouched.
            return;
        }
        readPackagedConfig();
        applyLevel(levelName);
    }

    private static void readPackagedConfig() {
        try (InputStream in = LoggingSupport.class.getClassLoader().getResourceAsStream(CONFIG_RESOURCE)) {
            if (in == null) {
                System.err.println("warning: " + CONFIG_RESOURCE + " not on the classpath; using JDK defaults");
                return;
            }
            LogManager.getLogManager().readConfiguration(in);
        } catch (IOException | SecurityException e) {
            System.err.println("warning: could not apply " + CONFIG_RESOURCE + " (" + e.getMessage()
                    + "); using JDK defaults");
        }
    }

    /**
     * Sets the level on the root logger and on every handler attached to it.
     *
     * <p>Both are necessary and this is the classic {@code java.util.logging} trap: a
     * handler left at {@code INFO} discards {@code FINE} records the logger happily
     * published, so {@code --pipeline.log.level=FINE} appears to do nothing.
     */
    private static void applyLevel(String levelName) {
        if (levelName == null) {
            // "Never throws" has to hold for null too: this method is one line away from
            // main(), and a NullPointerException here would abort a run over a log setting.
            System.err.println("warning: no log level supplied; keeping " + CONFIG_RESOURCE + "'s level");
            return;
        }
        Level level;
        try {
            level = Level.parse(levelName.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            System.err.println("warning: unknown log level '" + levelName + "'; keeping INFO");
            return;
        }
        Logger root = Logger.getLogger(ROOT_LOGGER);
        root.setLevel(level);
        for (Handler handler : root.getHandlers()) {
            handler.setLevel(level);
        }
    }
}
