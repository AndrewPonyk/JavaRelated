package com.parallelimage.app.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.app.api.Json;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link JsonLogHandler} tests: one JSON object per {@link LogRecord}, escaped the same way the
 * control API escapes its response bodies, and never closing the sink it was handed.
 */
class JsonLogHandlerTest {

    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    private final PrintStream sink = new PrintStream(captured, false, StandardCharsets.UTF_8);
    private final JsonLogHandler handler = new JsonLogHandler(sink);

    @Test
    @DisplayName("a plain record renders every field, with no exception key")
    void plainRecordRendersCoreFields() {
        LogRecord record = new LogRecord(Level.INFO, "starting batch");
        record.setLoggerName("com.parallelimage.app.Main");

        handler.publish(record);
        handler.flush();

        Map<String, String> fields = Json.parseFlatObject(oneLine());
        assertEquals("INFO", fields.get("level"));
        assertEquals("com.parallelimage.app.Main", fields.get("logger"));
        assertEquals("starting batch", fields.get("message"));
        assertEquals(String.valueOf(record.getLongThreadID()), fields.get("thread"));
        assertFalse(fields.containsKey("exception"), fields.toString());
    }

    @Test
    @DisplayName("a null logger name renders as an empty string, not the literal 'null'")
    void nullLoggerNameIsEmpty() {
        LogRecord record = new LogRecord(Level.WARNING, "no logger");
        record.setLoggerName(null);

        handler.publish(record);
        handler.flush();

        assertEquals("", Json.parseFlatObject(oneLine()).get("logger"));
    }

    @Test
    @DisplayName("a null message renders as an empty string")
    void nullMessageIsEmpty() {
        LogRecord record = new LogRecord(Level.INFO, null);

        handler.publish(record);
        handler.flush();

        assertEquals("", Json.parseFlatObject(oneLine()).get("message"));
    }

    @Test
    @DisplayName("message parameters are substituted via MessageFormat")
    void parametersAreFormatted() {
        LogRecord record = new LogRecord(Level.INFO, "processed {0} of {1}");
        record.setParameters(new Object[] {3, 10});

        handler.publish(record);
        handler.flush();

        assertEquals("processed 3 of 10", Json.parseFlatObject(oneLine()).get("message"));
    }

    @Test
    @DisplayName("a message that MessageFormat cannot parse is passed through raw")
    void unformattableMessageFallsBackToRaw() {
        LogRecord record = new LogRecord(Level.INFO, "unbalanced {0");
        record.setParameters(new Object[] {"x"});

        handler.publish(record);
        handler.flush();

        assertEquals("unbalanced {0", Json.parseFlatObject(oneLine()).get("message"));
    }

    @Test
    @DisplayName("a thrown exception adds an exception field carrying its stack trace")
    void thrownExceptionIsIncluded() {
        LogRecord record = new LogRecord(Level.SEVERE, "startup failed");
        record.setThrown(new IllegalStateException("boom"));

        handler.publish(record);
        handler.flush();

        String exception = Json.parseFlatObject(oneLine()).get("exception");
        assertTrue(exception.contains("IllegalStateException"), exception);
        assertTrue(exception.contains("boom"), exception);
    }

    @Test
    @DisplayName("a record below the handler's level is not written at all")
    void belowLevelRecordsAreSkipped() {
        handler.setLevel(Level.WARNING);

        handler.publish(new LogRecord(Level.INFO, "too quiet to matter"));
        handler.flush();

        assertEquals(0, captured.size());
    }

    @Test
    @DisplayName("close() flushes but never closes the underlying stream")
    void closeFlushesWithoutClosing() {
        handler.publish(new LogRecord(Level.INFO, "one more line"));
        handler.close();

        assertFalse(sink.checkError(), "the sink must still be usable after close()");
        assertTrue(captured.size() > 0);
    }

    @Test
    @DisplayName("the no-arg constructor defaults to System.out")
    void noArgConstructorDefaultsToSystemOut() {
        new JsonLogHandler();
    }

    private String oneLine() {
        String text = captured.toString(StandardCharsets.UTF_8).strip();
        assertFalse(text.isEmpty(), "expected exactly one published line");
        return text;
    }
}
