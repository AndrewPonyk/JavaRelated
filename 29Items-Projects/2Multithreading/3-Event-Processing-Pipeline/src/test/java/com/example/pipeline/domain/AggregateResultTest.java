package com.example.pipeline.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.pipeline.TestEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AggregateResult}.
 *
 * <p>This is the most important test class in the suite, and the reason is not obvious:
 * {@code merge} is what makes the parallel aggregation <em>legal</em>. The fork/join
 * stage splits a batch, folds the halves on different threads, and combines the results
 * — which is only correct if {@code merge} is associative and commutative with
 * {@code identity} as its neutral element. That is a monoid, and if any of those three
 * laws breaks, the aggregation silently produces a different answer depending on how
 * the work happened to be scheduled. No integration test would reliably catch it.
 *
 * <p>So the laws are tested directly, as laws, rather than by checking a few examples.
 */
class AggregateResultTest {

    private static final String SENSOR = "sensor-00";
    private static final double EPSILON = 1e-9;

    @Nested
    @DisplayName("monoid laws")
    class MonoidLaws {

        @Test
        @DisplayName("identity is neutral on both sides")
        void identityIsNeutral() {
            AggregateResult value = aggregateOf(1.0, 2.0, 3.0);
            AggregateResult identity = AggregateResult.identity(SENSOR);

            assertEquals(value, value.merge(identity), "right identity");
            assertEquals(value, identity.merge(value), "left identity");
            assertEquals(identity, identity.merge(identity), "identity is idempotent");
        }

        @Test
        @DisplayName("merge is associative")
        void mergeIsAssociative() {
            AggregateResult a = aggregateOf(5.0, 1.0);
            AggregateResult b = aggregateOf(-3.0, 9.0);
            AggregateResult c = aggregateOf(0.0, 4.5);

            // (a+b)+c == a+(b+c). This is the law fork/join depends on: the shape of
            // the split tree must not change the answer.
            assertEquals(a.merge(b).merge(c), a.merge(b.merge(c)));
        }

        @Test
        @DisplayName("merge is commutative")
        void mergeIsCommutative() {
            AggregateResult a = aggregateOf(5.0, 1.0);
            AggregateResult b = aggregateOf(-3.0, 9.0);

            // Commutativity is what makes the completion ORDER of the forked halves
            // irrelevant, which is the property a scheduler will test at random.
            assertEquals(a.merge(b), b.merge(a));
        }

        /**
         * The laws again, at scale, against a deliberately awkward fold order.
         *
         * <p>A fixed seed: this must be reproducible. It is a randomised check of a
         * property, not a search for new inputs on every CI run.
         */
        @Test
        @DisplayName("any fold order over 500 readings gives the same aggregate")
        void anyFoldOrderAgrees() {
            Random random = new Random(20260101L);
            List<SensorEvent> events = new ArrayList<>();
            for (int i = 0; i < 500; i++) {
                events.add(TestEvents.event(i, SENSOR, random.nextDouble() * 200.0 - 100.0));
            }

            AggregateResult leftFold = AggregateResult.identity(SENSOR);
            for (SensorEvent event : events) {
                leftFold = leftFold.accumulate(event);
            }

            // Same events, folded as a balanced binary tree of merges instead.
            AggregateResult treeFold = mergeTree(events, 0, events.size());

            assertEquals(leftFold.count(), treeFold.count());
            assertEquals(leftFold.min(), treeFold.min(), EPSILON);
            assertEquals(leftFold.max(), treeFold.max(), EPSILON);
            // sum, not equals: floating-point addition is not associative, so the two
            // orders may differ in the last bits. That is a property of doubles, not a
            // bug in merge -- and it is exactly why an epsilon belongs here and the
            // count/min/max above are compared exactly.
            assertEquals(leftFold.sum(), treeFold.sum(), 1e-6);
        }

        private AggregateResult mergeTree(List<SensorEvent> events, int from, int to) {
            if (to - from <= 1) {
                return AggregateResult.of(events.get(from));
            }
            int mid = (from + to) >>> 1;
            return mergeTree(events, from, mid).merge(mergeTree(events, mid, to));
        }
    }

    @Nested
    @DisplayName("identity")
    class Identity {

        /**
         * {@code identity} holds {@code +INF} as min and {@code -INF} as max, which
         * looks alarming and is the only choice that works: any real reading is both
         * smaller than {@code +INF} and larger than {@code -INF}, so the first
         * {@code accumulate} replaces both.
         */
        @Test
        @DisplayName("uses infinite bounds so the first reading replaces them")
        void usesInfiniteBounds() {
            AggregateResult identity = AggregateResult.identity(SENSOR);
            assertEquals(Double.POSITIVE_INFINITY, identity.min());
            assertEquals(Double.NEGATIVE_INFINITY, identity.max());
            assertTrue(identity.isEmpty());

            AggregateResult one = identity.accumulate(TestEvents.event(SENSOR, 7.0));
            assertEquals(7.0, one.min());
            assertEquals(7.0, one.max());
            assertFalse(one.isEmpty());
        }

        /**
         * The infinities must never reach a report. {@code average()} on an empty
         * aggregate would be {@code 0.0 / 0} = {@code NaN} if it divided blindly.
         */
        @Test
        @DisplayName("average of an empty aggregate is 0.0, never NaN")
        void averageOfEmptyIsZero() {
            assertEquals(0.0, AggregateResult.identity(SENSOR).average());
        }
    }

    @Nested
    @DisplayName("guards")
    class Guards {

        /**
         * Merging two different sensors would produce a plausible-looking row with a
         * count that is right and a min/max that belong to no sensor at all. Nothing
         * downstream could detect it, so it has to throw here.
         */
        @Test
        @DisplayName("refuses to merge aggregates for different sensors")
        void refusesToMergeDifferentSensors() {
            AggregateResult left = AggregateResult.of(TestEvents.event("sensor-00", 1.0));
            AggregateResult right = AggregateResult.of(TestEvents.event("sensor-01", 2.0));

            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> left.merge(right));
            assertTrue(thrown.getMessage().contains("sensor-00"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("sensor-01"), thrown.getMessage());
        }

        @Test
        @DisplayName("refuses to accumulate an event from another sensor")
        void refusesForeignEvent() {
            AggregateResult aggregate = AggregateResult.of(TestEvents.event("sensor-00", 1.0));
            assertThrows(IllegalArgumentException.class,
                    () -> aggregate.accumulate(TestEvents.event("sensor-09", 2.0)));
        }
    }

    @Nested
    @DisplayName("arithmetic")
    class Arithmetic {

        @Test
        @DisplayName("count, min, max and sum track the readings folded in")
        void tracksReadings() {
            AggregateResult result = aggregateOf(10.0, -5.0, 2.5, 42.0);
            assertEquals(4L, result.count());
            assertEquals(-5.0, result.min());
            assertEquals(42.0, result.max());
            assertEquals(49.5, result.sum(), EPSILON);
            assertEquals(49.5 / 4, result.average(), EPSILON);
        }

        /**
         * The mean is derived here and in the SQL view, never stored. A stored mean
         * cannot be merged — averaging two averages is wrong unless the counts are
         * equal — and mergeability is the whole basis of the parallel aggregation.
         */
        @Test
        @DisplayName("average is derived, so merging then averaging is exact")
        void averageIsDerived() {
            AggregateResult few = aggregateOf(100.0);
            AggregateResult many = aggregateOf(0.0, 0.0, 0.0);

            AggregateResult merged = few.merge(many);
            assertEquals(25.0, merged.average(), EPSILON, "weighted by count, not the mean of means");
            // The naive "average the averages" answer would be 50.0.
        }

        @Test
        @DisplayName("toDisplayRow renders the sensor and the derived mean")
        void displayRowRendersTheMean() {
            String row = aggregateOf(1.0, 3.0).toDisplayRow();
            assertTrue(row.contains(SENSOR), row);
            assertTrue(row.contains("2"), "the mean of 1 and 3 should appear: " + row);
        }
    }

    private static AggregateResult aggregateOf(double... values) {
        AggregateResult result = AggregateResult.identity(SENSOR);
        long sequence = 0L;
        for (double value : values) {
            result = result.accumulate(TestEvents.event(sequence++, SENSOR, value));
        }
        return result;
    }
}
