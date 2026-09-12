package com.example.pipeline.infrastructure.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.pipeline.domain.MetricsSnapshot;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests for {@link AtomicMetricsRecorder}.
 *
 * <p>A counter that loses increments under contention is worse than no counter: the
 * end-of-run reconciliation is computed from these numbers, so a lost increment turns
 * into a "we lost events" report about a run that lost nothing, and the next hour is
 * spent looking for a bug in the queue. So the test that matters is the concurrent one,
 * and it asserts an exact total rather than "roughly the right order of magnitude".
 */
class AtomicMetricsRecorderTest {

    @Nested
    @DisplayName("accumulation")
    class Accumulation {

        @Test
        @DisplayName("each counter sums the values recorded against it")
        void countersSum() {
            AtomicMetricsRecorder metrics = new AtomicMetricsRecorder();

            metrics.recordProduced(100L, 4L);
            metrics.recordProduced(50L, 2L);
            metrics.recordFiltered(90L, 60L);
            metrics.recordAggregated(90L, 6L);

            MetricsSnapshot snapshot = metrics.snapshot();
            assertEquals(150L, snapshot.eventsProduced());
            assertEquals(6L, snapshot.batchesProduced());
            assertEquals(90L, snapshot.eventsPassed());
            assertEquals(60L, snapshot.eventsRejected());
            assertEquals(90L, snapshot.eventsAggregated());
            assertEquals(6L, snapshot.batchesAggregated());
            assertEquals(150L, snapshot.eventsFiltered());
            assertTrue(snapshot.reconciles(), "150 produced, 90 + 60 decided");
            assertEquals(0L, snapshot.lostEvents());
        }

        /**
         * The failure the invariant exists to catch, constructed deliberately: ten
         * events were produced and only nine were ever decided about.
         */
        @Test
        @DisplayName("a missing event makes the snapshot fail to reconcile")
        void detectsLostEvents() {
            AtomicMetricsRecorder metrics = new AtomicMetricsRecorder();
            metrics.recordProduced(10L, 1L);
            metrics.recordFiltered(5L, 4L);

            MetricsSnapshot snapshot = metrics.snapshot();
            assertFalse(snapshot.reconciles());
            assertEquals(1L, snapshot.lostEvents());
            assertTrue(snapshot.toLogLine().contains("reconciled=false"), snapshot.toLogLine());
        }

        /**
         * A zero or negative duration is dropped rather than recorded, so an unsaturated
         * queue reports no entry at all. An entry holding {@code 0} would make
         * {@code mostBlockedQueue()} name a queue that never blocked.
         */
        @Test
        @DisplayName("non-positive blocked time is dropped")
        void dropsNonPositiveBlockedTime() {
            AtomicMetricsRecorder metrics = new AtomicMetricsRecorder();

            metrics.recordQueueBlocked("q1", 0L);
            metrics.recordQueueBlocked("q1", -5L);
            assertEquals(Map.of(), metrics.snapshot().queueBlockedNanos());
            assertEquals("none", metrics.snapshot().mostBlockedQueue());

            metrics.recordQueueBlocked("q1", 1_000_000L);
            metrics.recordQueueBlocked("q2", 3_000_000L);
            metrics.recordQueueBlocked("q1", 1_000_000L);

            assertEquals(Map.of("q1", 2_000_000L, "q2", 3_000_000L), metrics.snapshot().queueBlockedNanos());
            assertEquals(5_000_000L, metrics.snapshot().totalBlockedNanos());
            assertEquals("q2", metrics.snapshot().mostBlockedQueue());
        }

        @Test
        @DisplayName("errors are counted in total and attributed per stage")
        void errorsAreAttributed() {
            AtomicMetricsRecorder metrics = new AtomicMetricsRecorder();

            metrics.recordError("filter");
            metrics.recordError("filter");
            metrics.recordError("aggregate");

            assertEquals(3L, metrics.snapshot().errors());
            assertEquals(2L, metrics.errorsFor("filter"));
            assertEquals(1L, metrics.errorsFor("aggregate"));
            // An unknown stage is 0, not null and not an exception: callers render this
            // straight into a report line.
            assertEquals(0L, metrics.errorsFor("generate"));
        }

        @Test
        @DisplayName("rejects a null stage or queue name")
        void rejectsNulls() {
            AtomicMetricsRecorder metrics = new AtomicMetricsRecorder();
            assertThrows(NullPointerException.class, () -> metrics.recordError(null));
            assertThrows(NullPointerException.class, () -> metrics.recordQueueBlocked(null, 1L));
        }
    }

    @Nested
    @DisplayName("under contention")
    class Contention {

        private static final int THREADS = 8;
        private static final int INCREMENTS = 20_000;

        /**
         * Eight threads, released together by a latch so they actually overlap rather
         * than running one after another, and an exact expected total. This is the test
         * that would fail if a {@code LongAdder} were swapped for a plain {@code long}.
         */
        @Test
        @Timeout(60)
        @DisplayName("no increment is lost with 8 threads writing every counter")
        void losesNothing() throws InterruptedException {
            AtomicMetricsRecorder metrics = new AtomicMetricsRecorder();
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(THREADS);
            ExecutorService workers = Executors.newFixedThreadPool(THREADS);
            try {
                for (int t = 0; t < THREADS; t++) {
                    workers.execute(() -> {
                        try {
                            start.await();
                            for (int i = 0; i < INCREMENTS; i++) {
                                metrics.recordProduced(1L, 1L);
                                metrics.recordFiltered(1L, 0L);
                                metrics.recordAggregated(1L, 0L);
                                metrics.recordError("filter");
                                metrics.recordQueueBlocked("q", 1L);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.countDown();
                        }
                    });
                }
                start.countDown();
                assertTrue(done.await(45, TimeUnit.SECONDS), "the writers should have finished");
            } finally {
                workers.shutdownNow();
            }

            long expected = (long) THREADS * INCREMENTS;
            MetricsSnapshot snapshot = metrics.snapshot();
            assertEquals(expected, snapshot.eventsProduced());
            assertEquals(expected, snapshot.eventsPassed());
            assertEquals(expected, snapshot.eventsAggregated());
            assertEquals(expected, snapshot.errors());
            assertEquals(expected, metrics.errorsFor("filter"));
            assertEquals(expected, snapshot.totalBlockedNanos());
            assertTrue(snapshot.reconciles());
        }
    }

    @Nested
    @DisplayName("reset")
    class Reset {

        @Test
        @DisplayName("clears every counter, including the per-key maps")
        void clearsEverything() {
            AtomicMetricsRecorder metrics = new AtomicMetricsRecorder();
            metrics.recordProduced(10L, 1L);
            metrics.recordFiltered(6L, 4L);
            metrics.recordError("filter");
            metrics.recordQueueBlocked("q", 1_000L);

            metrics.reset();

            MetricsSnapshot snapshot = metrics.snapshot();
            assertEquals(0L, snapshot.eventsProduced());
            assertEquals(0L, snapshot.eventsFiltered());
            assertEquals(0L, snapshot.errors());
            // Cleared, not zeroed-in-place: a stale key would keep naming a queue that
            // no longer has any blocked time.
            assertEquals(Map.of(), snapshot.queueBlockedNanos());
            assertEquals(0L, metrics.errorsFor("filter"));
            assertTrue(snapshot.reconciles(), "0 == 0 + 0");
        }
    }
}
