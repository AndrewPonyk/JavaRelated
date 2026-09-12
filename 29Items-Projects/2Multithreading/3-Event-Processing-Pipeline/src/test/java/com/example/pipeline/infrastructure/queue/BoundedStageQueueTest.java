package com.example.pipeline.infrastructure.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.pipeline.TestEvents;
import com.example.pipeline.application.port.MetricsRecorder;
import com.example.pipeline.domain.EventBatch;
import com.example.pipeline.domain.PipelineMessage;
import com.example.pipeline.domain.PoisonPill;
import com.example.pipeline.infrastructure.metrics.AtomicMetricsRecorder;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests for {@link BoundedStageQueue}.
 *
 * <p>The queue is the only place backpressure exists, so these tests are about the
 * blocking behaviour rather than the trivial put/poll round trip. Two properties in
 * particular are load-bearing elsewhere:
 *
 * <ul>
 *   <li><strong>{@code put} blocks when full.</strong> If it silently dropped instead,
 *       the reconciliation invariant {@code produced == passed + rejected} would fail at
 *       the end of a run with no indication of where the events went.</li>
 *   <li><strong>{@code poll} returns {@code null} on timeout.</strong> That null is what
 *       lets a consumer loop re-check its stop flag; a consumer parked forever in
 *       {@code take()} is the classic non-terminating pipeline.</li>
 * </ul>
 *
 * <p>Nothing here asserts "this happened within N milliseconds". Latches and
 * {@code @Timeout} decide the outcome; a shared CI runner makes any wall-clock
 * assertion a guaranteed future flake.
 */
class BoundedStageQueueTest {

    private static final Duration SHORT = Duration.ofMillis(50);
    private static final Duration GENEROUS = Duration.ofSeconds(5);

    @Nested
    @DisplayName("ordering and basic transfer")
    class Transfer {

        @Test
        @Timeout(10)
        @DisplayName("messages come out in the order they went in")
        void isFifo() throws InterruptedException {
            BoundedStageQueue queue = new BoundedStageQueue("test", 8, MetricsRecorder.noOp());

            for (int i = 0; i < 5; i++) {
                queue.put(TestEvents.batch(i, 2, 1));
            }

            // FIFO is not a nicety here: the poison-pill shutdown is only safe because
            // a pill enqueued after the last batch is also DEQUEUED after it. A
            // reordering queue would let a consumer stop while data was still behind it.
            for (int i = 0; i < 5; i++) {
                PipelineMessage message = queue.poll(GENEROUS);
                assertEquals(i, assertInstanceOf(EventBatch.class, message).batchId());
            }
        }

        @Test
        @Timeout(10)
        @DisplayName("depth and utilisation track what is queued")
        void reportsItsOwnState() throws InterruptedException {
            BoundedStageQueue queue = new BoundedStageQueue("producer->filter", 4, MetricsRecorder.noOp());

            assertEquals(0, queue.depth());
            assertEquals(4, queue.capacity());
            assertEquals(0.0, queue.utilisation());
            assertEquals("producer->filter", queue.name());

            queue.put(TestEvents.batch(0, 1, 1));
            queue.put(TestEvents.batch(1, 1, 1));

            assertEquals(2, queue.depth());
            assertEquals(0.5, queue.utilisation());
            // The dashboard renders toString() directly, so the shape is part of the API.
            assertTrue(queue.toString().contains("2/4"), queue.toString());
        }

        @Test
        @DisplayName("rejects a capacity below 1 and a null message")
        void rejectsBadArguments() {
            assertThrows(IllegalArgumentException.class,
                    () -> new BoundedStageQueue("test", 0, MetricsRecorder.noOp()));
            BoundedStageQueue queue = new BoundedStageQueue("test", 1, MetricsRecorder.noOp());
            assertThrows(NullPointerException.class, () -> queue.put(null));
        }
    }

    @Nested
    @DisplayName("backpressure")
    class Backpressure {

        /**
         * The central test of the whole project. A capacity-1 queue is filled, a second
         * {@code put} is attempted on another thread, and the assertion is that the
         * thread is still inside {@code put} until something is taken out.
         */
        @Test
        @Timeout(20)
        @DisplayName("put blocks while the queue is full and completes once space appears")
        void putBlocksUntilSpaceAppears() throws InterruptedException {
            BoundedStageQueue queue = new BoundedStageQueue("test", 1, MetricsRecorder.noOp());
            queue.put(TestEvents.batch(0, 1, 1));

            CountDownLatch aboutToPut = new CountDownLatch(1);
            AtomicBoolean putReturned = new AtomicBoolean(false);
            Thread producer = new Thread(() -> {
                aboutToPut.countDown();
                try {
                    queue.put(TestEvents.batch(1, 1, 1));
                    putReturned.set(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "test-producer");
            producer.start();

            assertTrue(aboutToPut.await(5, TimeUnit.SECONDS));
            // A join with a timeout, not a sleep-then-check: if put() wrongly returned,
            // this returns early and the assertion below fails fast rather than after a
            // fixed sleep. The timeout is a bound on the wrong behaviour, not on the right.
            producer.join(200);
            assertFalse(putReturned.get(), "put must not have returned while the queue was full");
            assertTrue(producer.isAlive(), "the producer should still be parked in put()");

            assertEquals(0, assertInstanceOf(EventBatch.class, queue.poll(GENEROUS)).batchId());

            producer.join(TimeUnit.SECONDS.toMillis(10));
            assertFalse(producer.isAlive(), "the producer should have been released by the take");
            assertTrue(putReturned.get());
            assertEquals(1, assertInstanceOf(EventBatch.class, queue.poll(GENEROUS)).batchId());
        }

        /**
         * The blocked time is the diagnostic that turns "it feels slow" into a named
         * bottleneck, so it has to actually be recorded — and only when the put really
         * blocked. The fast path uses a non-blocking {@code offer} first precisely so
         * that an unsaturated queue reports zero rather than a pile of sub-microsecond
         * samples.
         */
        @Test
        @Timeout(20)
        @DisplayName("blocked time is recorded when full, and not when there is room")
        void recordsBlockedTimeOnlyWhenItBlocks() throws InterruptedException {
            AtomicMetricsRecorder metrics = new AtomicMetricsRecorder();
            BoundedStageQueue queue = new BoundedStageQueue("test", 1, metrics);

            queue.put(TestEvents.batch(0, 1, 1));
            assertEquals(0L, metrics.snapshot().totalBlockedNanos(), "an unsaturated put must not be timed");
            assertEquals(Map.of(), metrics.snapshot().queueBlockedNanos(),
                    "no entry at all, not an entry holding zero");

            CountDownLatch started = new CountDownLatch(1);
            Thread producer = new Thread(() -> {
                started.countDown();
                try {
                    queue.put(TestEvents.batch(1, 1, 1));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "test-producer");
            producer.start();

            assertTrue(started.await(5, TimeUnit.SECONDS));
            producer.join(100);
            queue.poll(GENEROUS);
            producer.join(TimeUnit.SECONDS.toMillis(10));

            assertTrue(metrics.snapshot().totalBlockedNanos() > 0L,
                    "a put that blocked must contribute blocked time");
            assertEquals("test", metrics.snapshot().mostBlockedQueue(),
                    "the blocked time must be attributed to the queue by name");
        }
    }

    @Nested
    @DisplayName("poll timeout")
    class PollTimeout {

        /**
         * {@code null}, not an exception and not a block forever. Every consumer loop is
         * written as {@code while (running) { m = poll(t); if (m == null) continue; }},
         * so this return value is the only reason a stop flag is ever observed.
         */
        @Test
        @Timeout(10)
        @DisplayName("returns null when nothing arrives")
        void returnsNullOnTimeout() throws InterruptedException {
            BoundedStageQueue queue = new BoundedStageQueue("test", 4, MetricsRecorder.noOp());
            assertNull(queue.poll(SHORT));
        }

        @Test
        @Timeout(10)
        @DisplayName("a zero timeout is legal and returns immediately")
        void zeroTimeoutIsLegal() throws InterruptedException {
            BoundedStageQueue queue = new BoundedStageQueue("test", 4, MetricsRecorder.noOp());
            assertNull(queue.poll(Duration.ZERO));

            queue.put(TestEvents.batch(0, 1, 1));
            // Zero timeout must still hand over a message that is already queued.
            assertInstanceOf(EventBatch.class, queue.poll(Duration.ZERO));
        }
    }

    @Nested
    @DisplayName("poison pills")
    class Pills {

        @Test
        @Timeout(10)
        @DisplayName("putPoisonPills enqueues exactly the requested count, behind the data")
        void enqueuesPillsBehindTheData() throws InterruptedException {
            BoundedStageQueue queue = new BoundedStageQueue("test", 8, MetricsRecorder.noOp());
            queue.put(TestEvents.batch(0, 1, 1));

            queue.putPoisonPills(3);

            assertInstanceOf(EventBatch.class, queue.poll(GENEROUS), "data must come out first");
            for (int i = 0; i < 3; i++) {
                assertInstanceOf(PoisonPill.class, queue.poll(GENEROUS), "pill " + i);
            }
            assertNull(queue.poll(SHORT), "nothing should be left");
        }

        @Test
        @Timeout(10)
        @DisplayName("zero pills is a no-op and a negative count is rejected")
        void guardsTheCount() throws InterruptedException {
            BoundedStageQueue queue = new BoundedStageQueue("test", 4, MetricsRecorder.noOp());

            queue.putPoisonPills(0);
            assertEquals(0, queue.depth());
            // A negative count means the caller derived it wrongly -- most likely from a
            // thread count. Failing here is how that arithmetic bug becomes visible.
            assertThrows(IllegalArgumentException.class, () -> queue.putPoisonPills(-1));
        }

        /**
         * Pills go through {@code put}, so they respect the bound like any other message.
         * The alternative — a side channel that bypasses the capacity — would mean a
         * shutdown could be the thing that finally exhausts memory.
         */
        @Test
        @Timeout(20)
        @DisplayName("pills respect the capacity bound rather than bypassing it")
        void pillsAreBounded() throws InterruptedException {
            BoundedStageQueue queue = new BoundedStageQueue("test", 2, MetricsRecorder.noOp());

            Thread filler = new Thread(() -> {
                try {
                    queue.putPoisonPills(4);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "test-pills");
            filler.start();

            filler.join(200);
            assertTrue(filler.isAlive(), "the 3rd pill must be waiting for room");
            assertEquals(2, queue.depth());

            for (int i = 0; i < 4; i++) {
                assertInstanceOf(PoisonPill.class, queue.poll(GENEROUS), "pill " + i);
            }
            filler.join(TimeUnit.SECONDS.toMillis(10));
            assertFalse(filler.isAlive());
        }
    }
}
