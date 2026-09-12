package com.example.pipeline.infrastructure.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.pipeline.domain.AggregateResult;
import com.example.pipeline.domain.AggregateSnapshot;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The console report table.
 *
 * <p>The {@link PrintStream} is injected rather than captured from {@code System.out}: the
 * table is the only thing this class produces, so asserting on the exact bytes is both the
 * cheapest and the strictest check available, and it leaves no global state to restore.
 *
 * <p>The assertion that has already earned its keep once is {@link #tableIsAsciiOnly()} —
 * an em-dash slipped into the empty-snapshot message during development and rendered as
 * mojibake on a {@code cp1252} console, which is exactly the failure a Windows operator
 * would report as "the report is corrupted".
 */
@Timeout(10)
@DisplayName("ConsoleAggregateSink")
class ConsoleAggregateSinkTest {

    private ByteArrayOutputStream captured;
    private PrintStream out;
    private ConsoleAggregateSink sink;

    @BeforeEach
    void setUp() {
        captured = new ByteArrayOutputStream();
        out = new PrintStream(captured, true, StandardCharsets.UTF_8);
        sink = new ConsoleAggregateSink(out);
    }

    private String printed() {
        out.flush();
        return captured.toString(StandardCharsets.UTF_8);
    }

    private static AggregateSnapshot snapshotOf(AggregateResult... results) {
        Map<String, AggregateResult> map = new java.util.LinkedHashMap<>();
        for (AggregateResult result : results) {
            map.put(result.sensorId(), result);
        }
        return new AggregateSnapshot(map);
    }

    @Test
    @DisplayName("null collaborators are rejected")
    void nullsAreRejected() {
        assertThrows(NullPointerException.class, () -> new ConsoleAggregateSink(null));
        assertThrows(NullPointerException.class, () -> sink.publish(null));
    }

    @Test
    @DisplayName("the System.out constructor is usable without an explicit stream")
    void defaultConstructorWorks() {
        // Nothing is published through it - that would write to the surefire log. The point
        // is only that the constructor main() actually calls does not throw.
        assertNotNull(new ConsoleAggregateSink());
    }

    @Test
    @DisplayName("an empty snapshot says so instead of printing an empty table")
    void emptySnapshotExplainsItself() {
        sink.publish(AggregateSnapshot.empty());
        String text = printed();
        assertTrue(text.contains("no events passed the filter - nothing to aggregate"), text);
        // A header with no rows under it reads as a bug in the aggregation stage rather
        // than as a threshold that rejected everything.
        assertFalse(text.contains("sensor"), "no header should be printed for an empty run: " + text);
    }

    @Test
    @DisplayName("a populated snapshot prints a header, a rule, one row per sensor and a total")
    void populatedSnapshotPrintsTheWholeTable() {
        sink.publish(snapshotOf(new AggregateResult("sensor-a", 3L, 1.0, 9.0, 15.0),
                new AggregateResult("sensor-b", 2L, 2.0, 4.0, 6.0)));

        List<String> lines = printed().lines().toList();
        // header, rule, two rows, rule, total.
        assertEquals(6, lines.size(), printed());
        assertTrue(lines.get(0).contains("sensor") && lines.get(0).contains("count"), lines.get(0));
        assertTrue(lines.get(1).chars().allMatch(c -> c == '-'), lines.get(1));
        assertTrue(lines.get(2).startsWith("sensor-a"), lines.get(2));
        assertTrue(lines.get(3).startsWith("sensor-b"), lines.get(3));
        assertTrue(lines.get(4).chars().allMatch(c -> c == '-'), lines.get(4));
        assertTrue(lines.get(5).contains("TOTAL") && lines.get(5).contains("5"), lines.get(5));
    }

    @Test
    @DisplayName("rows are sorted by sensor id, not by whatever order the map yielded")
    void rowsAreSortedBySensorId() {
        // Inserted in reverse so an unsorted implementation would fail rather than pass by luck.
        sink.publish(snapshotOf(new AggregateResult("sensor-c", 1L, 1.0, 1.0, 1.0),
                new AggregateResult("sensor-a", 1L, 1.0, 1.0, 1.0),
                new AggregateResult("sensor-b", 1L, 1.0, 1.0, 1.0)));

        String text = printed();
        int a = text.indexOf("sensor-a");
        int b = text.indexOf("sensor-b");
        int c = text.indexOf("sensor-c");
        assertTrue(a < b && b < c, "two runs of the same data must produce identical output: " + text);
    }

    @Test
    @DisplayName("the header and every row share one column layout")
    void columnsLineUp() {
        sink.publish(snapshotOf(new AggregateResult("sensor-a", 3L, 1.0, 9.0, 15.0)));
        List<String> lines = printed().lines().toList();
        // The rule is built from the header's length; a row that outgrew it would mean the
        // numbers no longer sit under their labels.
        assertEquals(lines.get(0).length(), lines.get(1).length(), printed());
        assertEquals(lines.get(0).length(), lines.get(2).length(), printed());
    }

    @Test
    @DisplayName("the total is the sum of the per-sensor counts")
    void totalSumsEveryCount() {
        sink.publish(snapshotOf(new AggregateResult("sensor-a", 7L, 1.0, 9.0, 30.0),
                new AggregateResult("sensor-b", 5L, 1.0, 9.0, 20.0)));
        assertTrue(printed().lines().anyMatch(l -> l.contains("TOTAL") && l.contains("12")), printed());
    }

    @Test
    @DisplayName("the table is ASCII only, so a cp1252 console renders it")
    void tableIsAsciiOnly() {
        sink.publish(snapshotOf(new AggregateResult("sensor-a", 1L, 1.5, 2.5, 4.0)));
        assertAsciiOnly(printed());
    }

    @Test
    @DisplayName("the empty-snapshot message is ASCII only too")
    void emptyMessageIsAsciiOnly() {
        sink.publish(AggregateSnapshot.empty());
        assertAsciiOnly(printed());
    }

    private static void assertAsciiOnly(String text) {
        for (int i = 0; i < text.length(); i++) {
            assertTrue(text.charAt(i) < 0x80,
                    "non-ASCII U+" + Integer.toHexString(text.charAt(i)) + " in: " + text);
        }
    }

    @Test
    @DisplayName("publishing twice prints the table twice - the sink holds no state")
    void publishIsRepeatable() {
        AggregateSnapshot snapshot = snapshotOf(new AggregateResult("sensor-a", 1L, 1.0, 1.0, 1.0));
        sink.publish(snapshot);
        sink.publish(snapshot);
        assertEquals(2, printed().lines().filter(l -> l.startsWith("sensor-a")).count(), printed());
    }
}
