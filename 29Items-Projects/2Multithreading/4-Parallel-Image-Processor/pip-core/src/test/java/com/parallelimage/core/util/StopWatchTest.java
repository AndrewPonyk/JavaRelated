package com.parallelimage.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link StopWatch} has no injectable clock seam (only {@link System#nanoTime()}), so these tests
 * measure real elapsed time with generous, environment-tolerant bounds rather than mocking time.
 */
class StopWatchTest {

    @Nested
    @DisplayName("elapsedNanos() / elapsedMillis()")
    class Elapsed {

        @Test
        @DisplayName("reports a positive, plausible duration after a real sleep")
        void reportsPositiveDurationAfterSleep() throws InterruptedException {
            StopWatch stopWatch = StopWatch.started();
            Thread.sleep(60);
            long millis = stopWatch.elapsedMillis();
            // Lower bound proves time is actually measured (kills "return 0" and "subtraction ->
            // addition" mutants, the latter of which would yield either a huge or a negative value
            // because System.nanoTime() base values are large absolute numbers). Upper bound is
            // generous to tolerate scheduler jitter without becoming flaky.
            assertTrue(millis >= 40, "expected at least 40ms elapsed, got " + millis);
            assertTrue(millis < 5_000, "expected well under 5s elapsed, got " + millis);
        }

        @Test
        @DisplayName("elapsedNanos() only grows as time passes")
        void elapsedNanosGrowsMonotonically() throws InterruptedException {
            StopWatch stopWatch = StopWatch.started();
            long first = stopWatch.elapsedNanos();
            Thread.sleep(20);
            long second = stopWatch.elapsedNanos();
            assertTrue(second > first, "elapsed nanos must strictly increase: " + first + " -> " + second);
        }

        @Test
        @DisplayName("elapsedMillis() is the nanosecond-to-millisecond conversion of elapsedNanos()")
        void elapsedMillisConvertsNanos() throws InterruptedException {
            StopWatch stopWatch = StopWatch.started();
            Thread.sleep(30);
            long nanos = stopWatch.elapsedNanos();
            long millis = stopWatch.elapsedMillis();
            long expected = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(nanos);
            // A few ms of slack because elapsedNanos() and elapsedMillis() are two separate reads.
            assertTrue(Math.abs(expected - millis) <= 5,
                    "expected ~" + expected + "ms, got " + millis + "ms");
        }
    }

    @Nested
    @DisplayName("megapixelsPerSecond()")
    class MegapixelsPerSecond {

        @Test
        @DisplayName("computes throughput as megapixels divided by elapsed seconds, not multiplied")
        void computesThroughputViaDivision() throws InterruptedException {
            StopWatch stopWatch = StopWatch.started();
            Thread.sleep(60);
            long elapsedMillis = stopWatch.elapsedMillis();
            // Guard the environment assumption the magnitude check below depends on.
            assertTrue(elapsedMillis < 500, "test environment too slow/jittery for this assertion");

            double rate = stopWatch.megapixelsPerSecond(500_000_000L);

            // Correct formula: (500 MP) / (elapsedSeconds) with elapsedSeconds in [0.06, 0.5) puts the
            // true value in roughly [1000, 8334]. A mutant that turns the denominator scaling or the
            // outer division into a multiplication collapses this into the tens/hundreds (caught by
            // the lower bound); a mutant that turns the numerator scaling into a multiplication
            // inflates it past 1e15 (caught by the upper bound).
            assertTrue(rate > 1_000.0d, "expected throughput > 1000 MP/s, got " + rate);
            assertTrue(rate < 100_000.0d, "expected throughput < 100000 MP/s, got " + rate);
        }

        @Test
        @DisplayName("scales linearly with the pixel count for a fixed elapsed time")
        void scalesLinearlyWithPixelCount() throws InterruptedException {
            StopWatch stopWatch = StopWatch.started();
            Thread.sleep(60);

            double small = stopWatch.megapixelsPerSecond(1_000_000L);
            double large = stopWatch.megapixelsPerSecond(10_000_000L);

            // Both calls happen microseconds apart against a baseline of tens of milliseconds, so the
            // ratio must be ~10 regardless of the (near-identical) elapsed time used in each call.
            assertTrue(large > small * 9.0d && large < small * 11.0d,
                    "expected ~10x scaling, got small=" + small + " large=" + large);
        }

        @Test
        @DisplayName("returns a finite, non-negative value for a zero pixel count")
        void zeroPixelsYieldsZeroThroughput() throws InterruptedException {
            StopWatch stopWatch = StopWatch.started();
            Thread.sleep(10);
            assertEquals(0.0d, stopWatch.megapixelsPerSecond(0L), 1e-9);
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTest {

        @Test
        @DisplayName("renders as '<elapsedMillis> ms'")
        void rendersElapsedMillisSuffixedWithMs() throws InterruptedException {
            StopWatch stopWatch = StopWatch.started();
            Thread.sleep(15);
            String rendered = stopWatch.toString();
            assertTrue(rendered.matches("\\d+ ms"), "expected '<number> ms', got '" + rendered + "'");
            long renderedMillis = Long.parseLong(rendered.substring(0, rendered.length() - 3));
            // toString() and the follow-up elapsedMillis() call are microseconds apart in principle,
            // but both are ordinary reads on a possibly-loaded machine: a GC pause or scheduler
            // preemption between the two calls is a timing accident, not evidence toString() computed
            // the wrong thing, so the slack here is generous enough to absorb that without becoming
            // flaky.
            assertTrue(Math.abs(renderedMillis - stopWatch.elapsedMillis()) <= 200,
                    "rendered millis should track elapsedMillis(): " + rendered);
        }

        @Test
        @DisplayName("is never blank")
        void isNeverBlank() {
            assertTrue(StopWatch.started().toString().endsWith(" ms"));
        }
    }
}
