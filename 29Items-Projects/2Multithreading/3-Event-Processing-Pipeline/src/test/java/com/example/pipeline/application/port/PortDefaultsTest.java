package com.example.pipeline.application.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.pipeline.domain.MetricsSnapshot;
import com.example.pipeline.domain.SensorEvent;
import com.example.pipeline.domain.SensorType;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The two default implementations the ports supply themselves.
 *
 * <p>Both exist so a stage can be constructed without its real collaborator — a saturation
 * run that wants no filtering, a benchmark that wants no counters — and both are the kind of
 * code that is never noticed until it misbehaves. The assertion that earns its keep is
 * {@link NoOpRecorder#snapshotIsEmptyRatherThanNull()}: a no-op recorder returning
 * {@code null} instead of an empty snapshot would turn "observability switched off" into a
 * {@link NullPointerException} inside a stage's reporting path, which is a far worse failure
 * than the overhead it was avoiding.
 */
@Timeout(10)
@DisplayName("port defaults")
class PortDefaultsTest {

    @Nested
    @DisplayName("EventPredicate.acceptAll()")
    class AcceptAll {

        @Test
        @DisplayName("accepts every event, whatever its type or value")
        void acceptsEverything() {
            EventPredicate predicate = EventPredicate.acceptAll();
            for (SensorType type : SensorType.values()) {
                assertTrue(predicate.test(event(type, type.minValue())), type.name());
                assertTrue(predicate.test(event(type, type.maxValue())), type.name());
            }
        }

        @Test
        @DisplayName("describes itself, because the description lands in the report")
        void describesItself() {
            // The report prints the rule that was applied; "accept-all" has to say so rather
            // than leave a blank line where an operator expects a threshold.
            assertEquals("accept-all", EventPredicate.acceptAll().description());
        }
    }

    @Nested
    @DisplayName("MetricsRecorder.noOp()")
    class NoOpRecorder {

        @Test
        @DisplayName("every recording method is a no-op that does not throw")
        void recordingIsSilent() {
            MetricsRecorder recorder = MetricsRecorder.noOp();
            recorder.recordProduced(10L, 1L);
            recorder.recordFiltered(6L, 4L);
            recorder.recordAggregated(6L, 1L);
            recorder.recordError("filter");
            recorder.recordQueueBlocked("raw", 1_000L);
            recorder.reset();
            // Nothing was counted: the point of the no-op is that a stage wired with it
            // behaves identically apart from the numbers it cannot report.
            assertEquals(0L, recorder.snapshot().eventsProduced());
            assertEquals(0L, recorder.snapshot().errors());
        }

        /**
         * The one way this class can break a run: a stage calls {@code snapshot()} on the
         * reporting path, so {@code null} here would be an NPE at the end of an otherwise
         * successful pipeline.
         */
        @Test
        @DisplayName("snapshot() is an empty snapshot, never null")
        void snapshotIsEmptyRatherThanNull() {
            MetricsSnapshot snapshot = MetricsRecorder.noOp().snapshot();
            assertEquals(MetricsSnapshot.empty(), snapshot);
            assertTrue(snapshot.reconciles(), "zero of everything reconciles");
        }

        @Test
        @DisplayName("each call returns a fresh recorder, so nothing is shared between runs")
        void instancesAreIndependent() {
            assertNotSame(MetricsRecorder.noOp(), MetricsRecorder.noOp());
        }
    }

    private static SensorEvent event(SensorType type, double value) {
        return new SensorEvent(1L, "sensor-0", type, value, Instant.parse("2026-08-18T00:00:00Z"));
    }
}
