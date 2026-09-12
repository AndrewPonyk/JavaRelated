package com.example.pipeline.presentation.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

/**
 * The log line's shape, which is a contract two other things depend on.
 *
 * <p>The thread-name column is why the class exists — {@code SimpleFormatter} cannot print
 * it, because a {@link LogRecord} carries a thread <em>id</em> and not a name — so the test
 * that matters most here is simply that the current thread's name appears. The rest pins the
 * alignment and the substitution behaviour that {@code LOG.log(level, "...{0}", arg)} call
 * sites throughout the pipeline rely on.
 *
 * <p>A {@link LogRecord} is constructed directly rather than captured from a handler: the
 * formatter is a pure function of the record plus the calling thread, so a real logger would
 * only add global state to restore afterwards.
 */
@Timeout(10)
@DisplayName("PipelineLogFormatter")
class PipelineLogFormatterTest {

    private final PipelineLogFormatter formatter = new PipelineLogFormatter();

    private static LogRecord record(Level level, String logger, String message) {
        LogRecord record = new LogRecord(level, message);
        record.setLoggerName(logger);
        record.setMillis(0L); // Deterministic; the timestamp text itself is not asserted.
        return record;
    }

    @Test
    @DisplayName("the line carries time, level, thread, short logger name and message")
    void lineHasEveryColumn() {
        String line = formatter.format(record(Level.INFO,
                "com.example.pipeline.application.stage.FilterStage", "threshold applied"));
        assertTrue(line.contains("[" + Thread.currentThread().getName() + "]"),
                "the thread column is the whole reason this formatter exists: " + line);
        assertTrue(line.contains("INFO"), line);
        assertTrue(line.contains("FilterStage - threshold applied"), line);
        assertFalse(line.contains("com.example.pipeline"),
                "the package is identical on every line and would cost 40 columns: " + line);
    }

    @Test
    @DisplayName("one record is one line when nothing was thrown")
    void oneRecordIsOneLine() {
        String line = formatter.format(record(Level.INFO, "a.b.C", "hello"));
        assertTrue(line.endsWith(System.lineSeparator()), "the line must be terminated");
        assertEquals(1, line.lines().count(), line);
    }

    @ParameterizedTest
    @DisplayName("the level is padded to a fixed width, so the message column stays aligned")
    @CsvSource({"INFO, 'INFO    ['", "FINE, 'FINE    ['", "WARNING, 'WARNING ['", "SEVERE, 'SEVERE  ['"})
    void levelIsPaddedToSevenColumns(String levelName, String expectedFragment) {
        String line = formatter.format(record(Level.parse(levelName), "a.b.C", "m"));
        assertTrue(line.contains(expectedFragment),
                "expected '" + expectedFragment + "' in: " + line);
    }

    @Test
    @DisplayName("a level name longer than the pad width is not truncated")
    void longLevelNamesAreNotTruncated() {
        // Level.FINEST is 6 characters; a custom level can exceed 7. Losing characters would
        // make two different levels look identical in the log.
        Level wide = new Level("VERYVERBOSE", Level.FINEST.intValue()) { };
        assertTrue(formatter.format(record(wide, "a.b.C", "m")).contains("VERYVERBOSE ["),
                "a wide level must push the column rather than lose characters");
    }

    @ParameterizedTest
    @DisplayName("a null or empty logger name renders as 'root' rather than as a gap")
    @NullAndEmptySource
    void anonymousLoggersRenderAsRoot(String loggerName) {
        assertTrue(formatter.format(record(Level.INFO, loggerName, "m")).contains("root - m"),
                "an empty column would look like a formatting bug");
    }

    @Test
    @DisplayName("a logger name with no dot is used as-is")
    void unqualifiedLoggerNameIsKept() {
        assertTrue(formatter.format(record(Level.INFO, "global", "m")).contains("global - m"));
    }

    @Test
    @DisplayName("{0} placeholders are substituted, not printed literally")
    void parametersAreSubstituted() {
        // formatMessage, not getMessage: LOG.log(Level.FINE, "batch {0} of {1}", ...) call
        // sites would otherwise log their own template.
        LogRecord record = record(Level.INFO, "a.b.C", "batch {0} of {1}");
        record.setParameters(new Object[] {7, 12});
        String line = formatter.format(record);
        assertTrue(line.contains("batch 7 of 12"), line);
        assertFalse(line.contains("{0}"), line);
    }

    @Test
    @DisplayName("a message with no parameters survives braces unchanged")
    void unparameterisedMessagesArePassedThrough() {
        // MessageFormat is only applied when parameters are present, so a message that
        // happens to contain a brace must not throw or be mangled.
        assertTrue(formatter.format(record(Level.INFO, "a.b.C", "json={\"a\":1}"))
                .contains("json={\"a\":1}"));
    }

    @Test
    @DisplayName("an attached throwable appends its full stack trace, including the cause")
    void throwableIsPrintedWithItsCause() {
        LogRecord record = record(Level.SEVERE, "a.b.C", "stage failed");
        record.setThrown(new IllegalStateException("outer", new java.io.IOException("root cause")));
        String text = formatter.format(record);
        assertTrue(text.contains("stage failed"), text);
        assertTrue(text.contains("java.lang.IllegalStateException: outer"), text);
        // A swallowed cause is behind half of all "it just stopped" reports.
        assertTrue(text.contains("Caused by: java.io.IOException: root cause"), text);
        assertTrue(text.lines().count() > 1, "a stack trace must span lines");
    }

    @Test
    @DisplayName("a null message is formatted rather than throwing, keeping the line structure")
    void nullMessageIsTolerated() {
        // LogRecord permits a null message, so the formatter must survive one; the columns
        // before it still have to be readable, because that is where the diagnosis starts.
        LogRecord record = new LogRecord(Level.INFO, null);
        record.setLoggerName("a.b.C");
        String line = formatter.format(record);
        assertTrue(line.contains("[" + Thread.currentThread().getName() + "] C - "), line);
        assertEquals(1, line.lines().count(), line);
    }
}
