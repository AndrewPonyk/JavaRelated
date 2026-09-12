package com.example.pipeline.application.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.pipeline.TestEvents;
import com.example.pipeline.domain.AggregateResult;
import com.example.pipeline.domain.SensorEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link AggregationTask}.
 *
 * <p>The whole strategy of this class is one assertion: <em>the parallel result must
 * equal {@link AggregationTask#aggregateSequentially}</em>. That reference
 * implementation is 6 lines and obviously correct, which is exactly what makes it
 * useful — comparing against it is a far stronger check than asserting a handful of
 * counts and minima by hand, and it stays valid when the splitting strategy changes.
 *
 * <p>Every test that submits to a pool carries a {@code @Timeout}. A fork/join task
 * that deadlocks does not fail — it hangs, and a hanging build is diagnosed by reading
 * a stack dump instead of a test name.
 */
class AggregationTaskTest {

    /**
     * A dedicated pool, never {@code commonPool()}. The production code makes the same
     * choice for the same reason: work submitted to the common pool competes with
     * parallel streams and {@code CompletableFuture} elsewhere in the JVM, so a hang
     * there would be caused by something this test does not control.
     */
    private ForkJoinPool pool;

    @BeforeEach
    void createPool() {
        pool = new ForkJoinPool(4);
    }

    @AfterEach
    void shutDownPool() throws InterruptedException {
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "the pool should have no work left");
    }

    @Nested
    @DisplayName("parallel equals sequential")
    class MatchesReference {

        /**
         * Cutoffs on both sides of the input size, so the test covers the case where no
         * split happens at all (cutoff 4096 over 2000 events) and the case where the
         * tree is deep (cutoff 1). Cutoff 1 is a pathological setting no one should use
         * in production; it is here because it maximises the number of merges and
         * therefore the chance of catching an ordering bug.
         */
        @ParameterizedTest
        @ValueSource(ints = {1, 2, 7, 64, 512, 4096})
        @Timeout(30)
        @DisplayName("for every cutoff, over 2000 events across 8 sensors")
        void everyCutoffAgrees(int cutoff) {
            List<SensorEvent> events = TestEvents.events(2000, 8);

            Map<String, AggregateResult> expected = AggregationTask.aggregateSequentially(events);
            Map<String, AggregateResult> actual = pool.invoke(AggregationTask.forEvents(events, cutoff));

            assertEquals(expected, actual);
            assertEquals(8, actual.size(), "one aggregate per sensor");
        }

        /**
         * Repeated, because a single pass proves nothing about a scheduler. The split
         * tree is deterministic but the interleaving of the worker threads is not, and
         * a merge that is wrong only under a particular completion order will pass once
         * and fail on the fortieth run.
         */
        @RepeatedTest(20)
        @Timeout(30)
        @DisplayName("repeatedly, because one scheduling is not a proof")
        void agreesUnderRepeatedScheduling() {
            List<SensorEvent> events = TestEvents.events(5000, 16);

            assertEquals(AggregationTask.aggregateSequentially(events),
                    pool.invoke(AggregationTask.forEvents(events, 128)));
        }

        @Test
        @Timeout(30)
        @DisplayName("a single sensor still splits and merges correctly")
        void singleSensor() {
            List<SensorEvent> events = TestEvents.events(1000, 1);

            Map<String, AggregateResult> actual = pool.invoke(AggregationTask.forEvents(events, 16));

            assertEquals(1, actual.size());
            AggregateResult aggregate = actual.get("sensor-0");
            assertEquals(1000L, aggregate.count());
            assertEquals(0.0, aggregate.min());
            assertEquals(999.0, aggregate.max());
            // 0+1+...+999, computed as a closed form rather than by looping in the test.
            assertEquals(999.0 * 1000 / 2, aggregate.sum(), 1e-6);
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @Timeout(10)
        @DisplayName("an empty range yields an empty map, not a failure")
        void emptyRange() {
            // The dispatcher can legitimately hand over an empty batch at end of run.
            assertEquals(Map.of(), pool.invoke(AggregationTask.forEvents(List.of(), 64)));
        }

        @Test
        @Timeout(10)
        @DisplayName("a sub-range aggregates only that slice")
        void subRange() {
            List<SensorEvent> events = TestEvents.events(100, 1);

            Map<String, AggregateResult> actual =
                    pool.invoke(new AggregationTask(events, 10, 20, 4));

            assertEquals(10L, actual.get("sensor-0").count());
            assertEquals(10.0, actual.get("sensor-0").min());
            assertEquals(19.0, actual.get("sensor-0").max());
        }

        @Test
        @DisplayName("rejects an invalid range and a cutoff below 1")
        void rejectsBadArguments() {
            List<SensorEvent> events = TestEvents.events(10, 1);

            assertThrows(IllegalArgumentException.class, () -> new AggregationTask(events, -1, 5, 4));
            assertThrows(IllegalArgumentException.class, () -> new AggregationTask(events, 0, 11, 4));
            assertThrows(IllegalArgumentException.class, () -> new AggregationTask(events, 6, 5, 4));
            // A cutoff of 0 would split forever; failing at construction beats a
            // StackOverflowError inside a fork/join worker, where the stack trace names
            // no test at all.
            assertThrows(IllegalArgumentException.class, () -> new AggregationTask(events, 0, 10, 0));
        }
    }

    @Nested
    @DisplayName("mergeInto")
    class MergeInto {

        @Test
        @DisplayName("merges per sensor and returns the target it mutated")
        void mergesPerSensorInPlace() {
            Map<String, AggregateResult> target = new HashMap<>(
                    AggregationTask.aggregateSequentially(TestEvents.events(10, 2)));
            Map<String, AggregateResult> source =
                    AggregationTask.aggregateSequentially(TestEvents.events(10, 2));

            Map<String, AggregateResult> returned = AggregationTask.mergeInto(target, source);

            // Returning the target rather than a fresh map is what lets compute() chain
            // merges up the tree without allocating a map per level.
            assertSame(target, returned);
            assertEquals(10L, returned.get("sensor-0").count(), "5 + 5");
        }

        @Test
        @DisplayName("a sensor present only in the source is added")
        void addsUnseenSensors() {
            Map<String, AggregateResult> target = new HashMap<>();
            Map<String, AggregateResult> source =
                    AggregationTask.aggregateSequentially(TestEvents.events(4, 4));

            assertEquals(4, AggregationTask.mergeInto(target, source).size());
        }
    }
}
