package com.parallelimage.core.fork;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link AdaptiveThrottle} tests.
 *
 * <p>There is no seam to inject a fake heap reading &mdash; the class is hard-wired to
 * {@link java.lang.management.ManagementFactory#getMemoryMXBean()} with no constructor, setter, or
 * package-private overload that takes a ratio directly. These tests therefore work with the real JVM
 * heap instead of a fake one: a live JVM that has loaded JUnit and this test class always has heap
 * usage strictly between empty and full, and it never sits at or above {@code THROTTLE_UTILISATION}
 * (0.85) during a normal test run. That is a real, if indirect, oracle for the cache's identity/refresh
 * behaviour and the arithmetic in {@code sample()}/{@code refresh()}.
 *
 * <p>A couple of pure boundary mutants are <em>not</em> targeted here because they would only produce
 * different behaviour at an exact heap value (utilisation exactly {@code 0.85}, or {@code max} exactly
 * {@code 0}) that cannot be reached without injecting a fake reading; see the class javadoc on the
 * corresponding methods and the mutation report for details.
 */
class AdaptiveThrottleTest {

    @Nested
    @DisplayName("shouldThrottleSplitting")
    class ShouldThrottleSplitting {

        @Test
        @DisplayName("is false under the heap pressure of a normal test run")
        void isFalseUnderNormalHeapPressure() {
            assertFalse(AdaptiveThrottle.shouldThrottleSplitting(),
                    "a healthy test JVM must not report heap pressure at/above 85%; if this fails, "
                            + "either the mutant always returns true, or negates the >= comparison");
        }
    }

    @Nested
    @DisplayName("sample")
    class SampleCache {

        @Test
        @DisplayName("never returns null and reports a utilisation in [0, 1]")
        void isNeverNullAndInRange() {
            AdaptiveThrottle.Sample sample = AdaptiveThrottle.sample();
            assertNotNull(sample, "replaced return value with null would fail every caller immediately");
            assertTrue(sample.heapUtilisation() >= 0.0d && sample.heapUtilisation() <= 1.0d,
                    "heap utilisation must be a clamped fraction, got " + sample.heapUtilisation());
        }

        @Test
        @DisplayName("a real JVM's heap utilisation is strictly between empty and full")
        void utilisationIsStrictlyBetweenZeroAndOne() {
            double utilisation = AdaptiveThrottle.sample().heapUtilisation();

            assertTrue(utilisation > 0.0d,
                    "a JVM that has loaded JUnit and this test class has used heap; exactly 0.0 means "
                            + "the max<=0 guard swallowed a real, positive ratio (negated conditional)");
            assertTrue(utilisation < 1.0d,
                    "utilisation is used/max; exactly 1.0 (via Math.min clamping) means division was "
                            + "replaced with multiplication, producing a value far above 1.0");
        }

        @Test
        @DisplayName("two calls inside the cache TTL return the exact same cached sample")
        void rapidCallsHitTheCache() {
            AdaptiveThrottle.sample(); // warm-up: keep class-loading/JIT cost out of the timed gap below
            AdaptiveThrottle.Sample first = AdaptiveThrottle.sample();
            AdaptiveThrottle.Sample second = AdaptiveThrottle.sample();

            assertSame(first, second,
                    "back-to-back calls are microseconds apart, far under the 5ms TTL: no refresh "
                            + "should happen, so the exact same Sample instance must come back");
        }

        @Test
        @DisplayName("a call after the cache TTL expires refreshes to a strictly later sample")
        void callAfterTtlExpiryRefreshes() throws InterruptedException {
            AdaptiveThrottle.sample(); // warm-up
            AdaptiveThrottle.Sample first = AdaptiveThrottle.sample();
            Thread.sleep(50);
            AdaptiveThrottle.Sample second = AdaptiveThrottle.sample();

            assertTrue(second.timestampNanos() > first.timestampNanos(),
                    "50ms is far past the 5ms TTL: refresh() must have run and produced a strictly "
                            + "newer timestamp, not handed back the stale cached sample (this also pins "
                            + "the subtraction-not-addition and boundary checks in sample()/refresh())");
        }
    }

    @Nested
    @DisplayName("concurrent access")
    class Concurrency {

        @Test
        @DisplayName("many threads hammering sample() across several TTL windows never see a bad sample")
        void manyThreadsNeverObserveANullOrInvalidSample() throws InterruptedException {
            int threadCount = 16;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            AtomicBoolean failed = new AtomicBoolean(false);
            long deadlineNanos = System.nanoTime() + 200_000_000L; // 200ms: many 5ms TTL windows

            try {
                for (int i = 0; i < threadCount; i++) {
                    executor.submit(() -> {
                        while (System.nanoTime() < deadlineNanos) {
                            AdaptiveThrottle.Sample sample = AdaptiveThrottle.sample();
                            if (sample == null
                                    || sample.heapUtilisation() < 0.0d
                                    || sample.heapUtilisation() > 1.0d) {
                                failed.set(true);
                            }
                        }
                    });
                }
                executor.shutdown();
                assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
            } finally {
                executor.shutdownNow();
            }

            assertFalse(failed.get(),
                    "the double-checked-locking refresh must never hand back a null or out-of-range "
                            + "sample under concurrent contention right at the cache boundary");
        }

        @Test
        @DisplayName("shouldThrottleSplitting never throws under concurrent load")
        void shouldThrottleSplittingNeverThrowsConcurrently() {
            assertDoesNotThrow(() -> {
                ExecutorService executor = Executors.newFixedThreadPool(8);
                try {
                    for (int i = 0; i < 8; i++) {
                        executor.submit(() -> {
                            for (int j = 0; j < 1_000; j++) {
                                AdaptiveThrottle.shouldThrottleSplitting();
                            }
                        });
                    }
                    executor.shutdown();
                    executor.awaitTermination(30, TimeUnit.SECONDS);
                } finally {
                    executor.shutdownNow();
                }
            });
        }
    }
}
