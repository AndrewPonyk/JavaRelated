package com.example.pipeline.application.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.pipeline.domain.AggregateResult;
import com.example.pipeline.domain.AggregateSnapshot;
import com.example.pipeline.domain.StageStats;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The aggregation stage's return value: tallies alongside results.
 *
 * <p>Small by design, and the assertion that earns its keep is
 * {@link #emptyIsFullyPopulated()}: {@code empty(stage)} exists so a run that aggregated
 * nothing still returns both components, and a caller rendering the report never has to
 * null-check either. Returning {@code null} for the snapshot of an empty run is the bug this
 * record's compact constructor and factory together rule out.
 */
@Timeout(10)
@DisplayName("AggregationOutcome")
class AggregationOutcomeTest {

    private static final StageStats STATS =
            new StageStats("aggregation", 2L, 20L, 20L, 0L, 0L, Duration.ofMillis(100L));

    private static AggregateSnapshot snapshot() {
        return new AggregateSnapshot(Map.of("sensor-a", new AggregateResult("sensor-a", 4L, 1.0, 9.0, 20.0)));
    }

    @Test
    @DisplayName("both components are required")
    void nullsAreRejected() {
        assertThrows(NullPointerException.class, () -> new AggregationOutcome(null, snapshot()));
        assertThrows(NullPointerException.class, () -> new AggregationOutcome(STATS, null));
    }

    @Test
    @DisplayName("the components are handed back as given")
    void componentsAreExposed() {
        AggregateSnapshot snapshot = snapshot();
        AggregationOutcome outcome = new AggregationOutcome(STATS, snapshot);
        // Both are immutable, so the record stores the references rather than copying.
        assertSame(STATS, outcome.stats());
        assertSame(snapshot, outcome.snapshot());
    }

    /**
     * A stage that aggregated nothing is a normal outcome — every event was filtered out —
     * and it must still carry a named {@code StageStats} and an empty snapshot rather than
     * holes for the report to trip over.
     */
    @Test
    @DisplayName("empty() populates both components and keeps the stage name")
    void emptyIsFullyPopulated() {
        AggregationOutcome outcome = AggregationOutcome.empty("aggregation");
        assertEquals("aggregation", outcome.stats().stage());
        assertEquals(0L, outcome.stats().eventsIn());
        assertEquals(Duration.ZERO, outcome.stats().elapsed());
        assertTrue(outcome.snapshot().isEmpty());
        assertEquals(0L, outcome.snapshot().totalCount());
    }

    @Test
    @DisplayName("two outcomes over equal components are equal")
    void equalityIsByValue() {
        // Value equality is what lets a test assert on a whole stage result in one line.
        assertEquals(new AggregationOutcome(STATS, snapshot()), new AggregationOutcome(STATS, snapshot()));
        assertEquals(AggregationOutcome.empty("aggregation").hashCode(),
                AggregationOutcome.empty("aggregation").hashCode());
        assertNotEquals(AggregationOutcome.empty("aggregation"), AggregationOutcome.empty("filter"));
    }

    @Test
    @DisplayName("toString names both components, so a failed assertion is readable")
    void toStringIsDiagnosable() {
        String text = new AggregationOutcome(STATS, snapshot()).toString();
        assertTrue(text.contains("aggregation"), text);
        assertTrue(text.contains("sensor-a"), text);
    }
}
