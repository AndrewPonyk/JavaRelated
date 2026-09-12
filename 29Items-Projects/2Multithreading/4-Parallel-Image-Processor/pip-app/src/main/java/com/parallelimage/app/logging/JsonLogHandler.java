package com.parallelimage.app.logging;

import com.parallelimage.app.api.Json;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * A {@link Handler} that writes one JSON object per {@link LogRecord}, for deployments that feed
 * their console output into a log aggregator rather than a human terminal.
 *
 * <p>Deliberately ignores any configured {@link #setFormatter}: the JSON shape here — timestamp,
 * level, logger, thread, message, exception — is the contract consumers parse against, and a
 * pluggable {@code Formatter} would let that contract drift silently. Escaping reuses
 * {@link Json#quote}, the same routine the control API uses for its response bodies, rather than
 * a second hand-rolled escaper.
 *
 * <p>Like {@link java.util.logging.ConsoleHandler}, {@link #close()} only flushes rather than
 * closing the underlying stream — the default target is {@link System#out}, and closing that out
 * from under the rest of the JVM would be far worse than leaving a handler attached past its
 * usefulness.
 */
public final class JsonLogHandler extends Handler {

    private final PrintStream out;

    public JsonLogHandler() {
        this(System.out);
    }

    public JsonLogHandler(PrintStream out) {
        this.out = out;
    }

    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }
        StringBuilder json = new StringBuilder(256)
                .append("{\"timestamp\":")
                .append(Json.quote(Instant.ofEpochMilli(record.getMillis()).toString()))
                .append(",\"level\":").append(Json.quote(record.getLevel().getName()))
                .append(",\"logger\":")
                .append(Json.quote(record.getLoggerName() == null ? "" : record.getLoggerName()))
                .append(",\"thread\":").append(record.getLongThreadID())
                .append(",\"message\":").append(Json.quote(formatMessage(record)));
        Throwable thrown = record.getThrown();
        if (thrown != null) {
            json.append(",\"exception\":").append(Json.quote(stackTraceOf(thrown)));
        }
        json.append('}');
        synchronized (this) {
            out.println(json);
        }
    }

    @Override
    public void flush() {
        out.flush();
    }

    @Override
    public void close() {
        flush();
    }

    private static String formatMessage(LogRecord record) {
        String message = record.getMessage();
        if (message == null) {
            return "";
        }
        Object[] params = record.getParameters();
        if (params == null || params.length == 0) {
            return message;
        }
        try {
            return MessageFormat.format(message, params);
        } catch (IllegalArgumentException e) {
            return message;
        }
    }

    private static String stackTraceOf(Throwable thrown) {
        StringWriter sw = new StringWriter();
        thrown.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
