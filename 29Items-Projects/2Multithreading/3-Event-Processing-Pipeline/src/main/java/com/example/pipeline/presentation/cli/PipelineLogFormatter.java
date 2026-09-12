package com.example.pipeline.presentation.cli;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * One-line log format that includes the thread name.
 *
 * <p><strong>Why this class exists at all.</strong> In a three-stage pipeline the first
 * question about any log line is which stage emitted it, and the JDK's
 * {@code SimpleFormatter} cannot answer it: its format arguments are timestamp, source,
 * logger, level, message and thrown — there is no thread among them, because
 * {@link LogRecord} carries only a thread <em>id</em>. A pattern in
 * {@code logging.properties} therefore cannot print {@code pipeline-consumer-2}, and one
 * short class here buys back the single most useful column in the log.
 *
 * <p><strong>Why {@code Thread.currentThread()} is the right thread.</strong>
 * {@code ConsoleHandler} publishes synchronously on the thread that called the logger, so
 * formatting happens there too. Resolving the id back to a live {@code Thread} would be
 * both slower and wrong — by then the emitting thread may have terminated. A handler that
 * queued records for a background writer would break this assumption, which is one more
 * reason the packaged configuration uses {@code ConsoleHandler} only.
 *
 * <p>Output is deliberately one line per record plus the stack trace when a throwable is
 * attached — greppable, and stable when redirected to a file or captured by CI.
 */
public final class PipelineLogFormatter extends Formatter {

    /** Width the level is padded to, so the message column stays aligned. */
    private static final int LEVEL_WIDTH = 7;

    /** Local wall-clock time; the date is noise for a run that lasts seconds. */
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.ROOT).withZone(ZoneId.systemDefault());

    /** Public no-arg constructor: {@code java.util.logging} instantiates this reflectively. */
    public PipelineLogFormatter() {
    }

    @Override
    public String format(LogRecord record) {
        StringBuilder line = new StringBuilder(160);
        line.append(TIME.format(Instant.ofEpochMilli(record.getMillis())));
        line.append(' ');
        appendPadded(line, record.getLevel().getName());
        line.append(" [").append(Thread.currentThread().getName()).append("] ");
        line.append(shortLoggerName(record.getLoggerName())).append(" - ");
        // formatMessage, not getMessage: it applies the {0}-style parameter substitution
        // that LOG.log(Level.FINE, "...{0}", value) call sites rely on.
        line.append(formatMessage(record));
        line.append(System.lineSeparator());
        appendThrown(line, record);
        return line.toString();
    }

    private static void appendPadded(StringBuilder out, String level) {
        out.append(level);
        for (int i = level.length(); i < LEVEL_WIDTH; i++) {
            out.append(' ');
        }
    }

    /**
     * {@code com.example.pipeline.application.stage.FilterStage} to {@code FilterStage}.
     *
     * <p>The package is fixed for every logger in this application, so printing it wastes
     * 40 columns on every line and pushes the message off an 80-column terminal.
     */
    private static String shortLoggerName(String loggerName) {
        if (loggerName == null || loggerName.isEmpty()) {
            return "root";
        }
        int lastDot = loggerName.lastIndexOf('.');
        return lastDot < 0 ? loggerName : loggerName.substring(lastDot + 1);
    }

    private static void appendThrown(StringBuilder out, LogRecord record) {
        Throwable thrown = record.getThrown();
        if (thrown == null) {
            return;
        }
        // A swallowed cause is the reason for half of all "it just stopped" reports, so the
        // full chain is printed even though it breaks the one-line-per-record rule.
        StringWriter buffer = new StringWriter();
        try (PrintWriter writer = new PrintWriter(buffer)) {
            thrown.printStackTrace(writer);
        }
        out.append(buffer);
    }
}
