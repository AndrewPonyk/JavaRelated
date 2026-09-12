package com.example.pipeline.application.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.pipeline.TestEvents;
import com.example.pipeline.application.filter.ThresholdPredicate;
import com.example.pipeline.application.port.EventPredicate;
import com.example.pipeline.application.port.MetricsRecorder;
import com.example.pipeline.domain.EventBatch;
import com.example.pipeline.domain.PipelineMessage;
import com.example.pipeline.domain.PoisonPill;
import com.example.pipeline.domain.SensorEvent;
import com.example.pipeline.domain.StageStats;
import com.example.pipeline.infrastructure.metrics.AtomicMetricsRecorder;
import com.example.pipeline.infrastructure.queue.BoundedStageQueue;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link FilterStage}.
 *
 * <p>Deliberately an integration test of the stage against the <em>real</em>
 * {@link BoundedStageQueue}, not a mock. The bugs this stage can have are all about
 * blocking: a pill re-enqueued onto a full upstream queue, a consumer parked in a poll
 * that never returns, a pill forwarded downstream before the last consumer has drained.
 * A mock channel that never blocks cannot exhibit any of them, so it would test the
 * arithmetic and none of the concurrency.
 *
 * <p>Two properties carry the shutdown correctness:
 * <ul>
 *   <li><strong>N pills stop N consumers</strong> — one each, no re-enqueue.</li>
 *   <li><strong>exactly one pill goes downstream</strong>, forwarded by whichever
 *       consumer happens to finish last. Forwarding one per consumer would stop the
 *       aggregator at the first and discard everything still in flight.</li>
 * </ul>
 */
class FilterStageTest {

    private static final Duration POLL = Duration.ofMillis(50);
    private static final Duration GENEROUS = Duration.ofSeconds(5);

    private ExecutorService consumers;

    @AfterEach
    void shutDownConsumers() {
        if (consumers != null) {
            consumers.shutdownNow();
        }
    }

    @Nested
    @DisplayName("filtering")
    class Filtering {

        /**
         * The expected counts are arithmetic, not hand-counted: {@code TestEvents} emits
         * ascending values {@code 0..999}, so a threshold of {@code t} passes exactly
         * {@code 999 - t} of them. That is what makes the assertion readable at 1000
         * events instead of 10.
         */
        @ParameterizedTest
        @ValueSource(ints = {0, 1, 499, 998})
        @Timeout(30)
        @DisplayName("passes exactly the events above the threshold and counts the rest")
        void passesAboveThresholdOnly(int threshold) throws Exception {
            BoundedStageQueue input = queue("in", 64);
            BoundedStageQueue output = queue("out", 64);
            AtomicMetricsRecorder metrics = new AtomicMetricsRecorder();
            FilterStage stage = new FilterStage(input, output, new ThresholdPredicate(threshold),
                    metrics, executor(2), 2, POLL);

            CompletableFuture<StageStats> done = stage.startAsync();
            for (int i = 0; i < 1000; i += 100) {
                input.put(new EventBatch(i / 100, TestEvents.events(1000, 4).subList(i, i + 100)));
            }
            input.putPoisonPills(2);

            StageStats stats = done.get(20, TimeUnit.SECONDS);

            long expectedPassed = 999L - threshold;
            assertEquals(1000L, stats.eventsIn());
            assertEquals(expectedPassed, stats.eventsOut());
            assertEquals(1000L - expectedPassed, stats.eventsRejected());
            assertEquals(FilterStage.NAME, stats.stage());
            // The same numbers must reach the metrics, because that is what the
            // end-of-run reconciliation is computed from -- not from these stats.
            assertEquals(expectedPassed, metrics.snapshot().eventsPassed());
            assertEquals(1000L - expectedPassed, metrics.snapshot().eventsRejected());
            assertEquals(1000L, metrics.snapshot().eventsFiltered());
        }

        /**
         * A batch with no survivors must not be forwarded. Forwarding it would be
         * harmless arithmetically but would cost a queue slot and a downstream fork/join
         * submission per empty batch — and {@code EventBatch} rejects an empty list
         * anyway, so the alternative is a crash.
         */
        @Test
        @Timeout(30)
        @DisplayName("an entirely rejected batch is counted but not forwarded")
        void doesNotForwardEmptyBatches() throws Exception {
            BoundedStageQueue input = queue("in", 8);
            BoundedStageQueue output = queue("out", 8);
            FilterStage stage = new FilterStage(input, output, new ThresholdPredicate(10_000.0),
                    MetricsRecorder.noOp(), executor(1), 1, POLL);

            CompletableFuture<StageStats> done = stage.startAsync();
            input.put(TestEvents.batch(0, 10, 2));
            input.putPoisonPills(1);

            StageStats stats = done.get(20, TimeUnit.SECONDS);

            assertEquals(10L, stats.eventsRejected());
            assertEquals(0L, stats.eventsOut());
            // Only the forwarded pill should be downstream -- no empty batch ahead of it.
            assertInstanceOf(PoisonPill.class, output.poll(GENEROUS));
            assertNull(output.poll(POLL));
        }

        @Test
        @Timeout(30)
        @DisplayName("the batch id survives the filter, so a survivor can be traced upstream")
        void preservesBatchIds() throws Exception {
            BoundedStageQueue input = queue("in", 8);
            BoundedStageQueue output = queue("out", 8);
            FilterStage stage = new FilterStage(input, output, new ThresholdPredicate(-1.0),
                    MetricsRecorder.noOp(), executor(1), 1, POLL);

            CompletableFuture<StageStats> done = stage.startAsync();
            input.put(TestEvents.batch(4242L, 4, 2));
            input.putPoisonPills(1);
            done.get(20, TimeUnit.SECONDS);

            EventBatch forwarded = assertInstanceOf(EventBatch.class, output.poll(GENEROUS));
            assertEquals(4242L, forwarded.batchId(), "a renumbered batch cannot be correlated with its source");
            assertEquals(4, forwarded.size());
        }
    }

    @Nested
    @DisplayName("poison pill shutdown")
    class Shutdown {

        /**
         * N pills, N consumers, and the stage completes. If a consumer re-enqueued the
         * pill it saw, one consumer would consume two pills and another would never see
         * one — the stage would hang and this test would fail on its timeout rather than
         * on an assertion, which is why the timeout is here at all.
         */
        @ParameterizedTest
        @ValueSource(ints = {1, 2, 4, 8})
        @Timeout(30)
        @DisplayName("one pill per consumer stops every consumer")
        void onePillPerConsumerStopsAll(int consumerCount) throws Exception {
            BoundedStageQueue input = queue("in", 64);
            BoundedStageQueue output = queue("out", 64);
            FilterStage stage = new FilterStage(input, output, new ThresholdPredicate(-1.0),
                    MetricsRecorder.noOp(), executor(consumerCount), consumerCount, POLL);

            CompletableFuture<StageStats> done = stage.startAsync();
            input.putPoisonPills(consumerCount);

            assertEquals(0L, done.get(20, TimeUnit.SECONDS).batches());
            assertNull(input.poll(POLL), "no pill may be left behind or re-enqueued");
        }

        /**
         * Exactly one pill downstream, regardless of how many consumers there were.
         * This is the countdown in {@code forwardPillIfLast}, and it is the one place
         * where the pill arithmetic differs between the two hops.
         */
        @ParameterizedTest
        @ValueSource(ints = {1, 2, 4, 8})
        @Timeout(30)
        @DisplayName("exactly one pill is forwarded downstream, not one per consumer")
        void forwardsExactlyOnePillDownstream(int consumerCount) throws Exception {
            BoundedStageQueue input = queue("in", 64);
            BoundedStageQueue output = queue("out", 64);
            FilterStage stage = new FilterStage(input, output, new ThresholdPredicate(-1.0),
                    MetricsRecorder.noOp(), executor(consumerCount), consumerCount, POLL);

            CompletableFuture<StageStats> done = stage.startAsync();
            input.put(TestEvents.batch(0, 4, 2));
            input.putPoisonPills(consumerCount);
            done.get(20, TimeUnit.SECONDS);

            List<PipelineMessage> drained = new ArrayList<>();
            for (PipelineMessage message = output.poll(GENEROUS);
                    message != null;
                    message = output.poll(POLL)) {
                drained.add(message);
            }

            assertEquals(1L, drained.stream().filter(PoisonPill.class::isInstance).count(),
                    "downstream saw " + drained.size() + " messages: " + drained);
        }

        /**
         * The pill is dequeued after the data, so every batch is filtered before any
         * consumer stops. This is FIFO doing the work — with a reordering queue the
         * poison-pill pattern would need a separate barrier.
         */
        @RepeatedTest(10)
        @Timeout(30)
        @DisplayName("data queued before the pills is never dropped")
        void drainsDataAheadOfThePills() throws Exception {
            int consumerCount = 4;
            // A capacity of 2 forces the producing thread (this one) to block in put(),
            // which is the state a drain bug needs in order to show itself.
            BoundedStageQueue input = queue("in", 2);
            BoundedStageQueue output = queue("out", 256);
            FilterStage stage = new FilterStage(input, output, new ThresholdPredicate(-1.0),
                    MetricsRecorder.noOp(), executor(consumerCount), consumerCount, POLL);

            CompletableFuture<StageStats> done = stage.startAsync();
            for (int i = 0; i < 50; i++) {
                input.put(TestEvents.batch(i, 20, 4));
            }
            input.putPoisonPills(consumerCount);

            StageStats stats = done.get(20, TimeUnit.SECONDS);

            assertEquals(50L, stats.batches());
            assertEquals(1000L, stats.eventsIn(), "50 batches x 20 events, none dropped at shutdown");
            assertEquals(1000L, stats.eventsOut());
        }

        /**
         * The cooperative stop, which exists for the case where no pill is coming — a
         * Ctrl+C while the producer is still running. It is only observable because the
         * consumer loop uses {@code poll(timeout)} rather than {@code take()}.
         */
        @Test
        @Timeout(30)
        @DisplayName("requestStop ends the consumers without any pill at all")
        void requestStopEndsTheStage() throws Exception {
            BoundedStageQueue input = queue("in", 8);
            BoundedStageQueue output = queue("out", 8);
            FilterStage stage = new FilterStage(input, output, new ThresholdPredicate(-1.0),
                    MetricsRecorder.noOp(), executor(3), 3, POLL);

            CompletableFuture<StageStats> done = stage.startAsync();
            // Nothing is ever enqueued: every consumer is looping on an empty queue.
            assertThrows(TimeoutException.class, () -> done.get(200, TimeUnit.MILLISECONDS),
                    "the consumers should still be polling");

            stage.requestStop();

            assertEquals(0L, done.get(20, TimeUnit.SECONDS).batches());
        }
    }

    @Nested
    @DisplayName("failure handling")
    class Failures {

        /**
         * A predicate that throws is a bug in the predicate, not a recoverable
         * condition, so the stage completes exceptionally rather than swallowing it —
         * silently rejecting every event would look exactly like a working filter with
         * an aggressive threshold.
         */
        @Test
        @Timeout(30)
        @DisplayName("a throwing predicate completes the stage exceptionally")
        void aThrowingPredicateSurfaces() throws Exception {
            BoundedStageQueue input = queue("in", 8);
            BoundedStageQueue output = queue("out", 8);
            EventPredicate exploding = new EventPredicate() {
                @Override
                public boolean test(SensorEvent event) {
                    throw new IllegalStateException("predicate blew up");
                }

                @Override
                public String description() {
                    return "always throws";
                }
            };
            FilterStage stage = new FilterStage(input, output, exploding,
                    MetricsRecorder.noOp(), executor(1), 1, POLL);

            CompletableFuture<StageStats> done = stage.startAsync();
            input.put(TestEvents.batch(0, 4, 1));

            ExecutionException thrown = assertThrows(ExecutionException.class,
                    () -> done.get(20, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, thrown.getCause());

            // And the pill still went downstream, from the finally block. A stage that
            // died without forwarding it would leave the aggregator waiting for a
            // message that is never coming.
            assertInstanceOf(PoisonPill.class, output.poll(GENEROUS));
        }

        @Test
        @DisplayName("rejects a consumer count below 1, a non-positive poll timeout and nulls")
        void rejectsBadArguments() {
            BoundedStageQueue input = queue("in", 8);
            BoundedStageQueue output = queue("out", 8);
            EventPredicate predicate = new ThresholdPredicate(0.0);
            ExecutorService pool = executor(1);

            assertThrows(IllegalArgumentException.class, () -> new FilterStage(
                    input, output, predicate, MetricsRecorder.noOp(), pool, 0, POLL));
            // A zero poll timeout would spin a consumer thread at 100% CPU; a negative
            // one is nonsense that ArrayBlockingQueue would silently treat as zero.
            assertThrows(IllegalArgumentException.class, () -> new FilterStage(
                    input, output, predicate, MetricsRecorder.noOp(), pool, 1, Duration.ZERO));
            assertThrows(IllegalArgumentException.class, () -> new FilterStage(
                    input, output, predicate, MetricsRecorder.noOp(), pool, 1, Duration.ofMillis(-1)));
            assertThrows(NullPointerException.class, () -> new FilterStage(
                    null, output, predicate, MetricsRecorder.noOp(), pool, 1, POLL));
            assertThrows(NullPointerException.class, () -> new FilterStage(
                    input, output, null, MetricsRecorder.noOp(), pool, 1, POLL));
        }

        @Test
        @DisplayName("consumerCount is reported back, so the producer can derive its pill count")
        void reportsItsConsumerCount() {
            FilterStage stage = new FilterStage(queue("in", 8), queue("out", 8),
                    new ThresholdPredicate(0.0), MetricsRecorder.noOp(), executor(6), 6, POLL);

            // The orchestrator reads this rather than re-deriving it from config, which
            // is how the pill count and the thread count are kept from drifting.
            assertEquals(6, stage.consumerCount());
        }
    }

    private static BoundedStageQueue queue(String name, int capacity) {
        return new BoundedStageQueue(name, capacity, MetricsRecorder.noOp());
    }

    private ExecutorService executor(int threads) {
        // One pool per test, shut down in @AfterEach. A shared pool would let a hung
        // consumer from an earlier test starve a later one, and the failure would name
        // the wrong test.
        assertTrue(threads >= 1);
        consumers = Executors.newFixedThreadPool(threads);
        return consumers;
    }
}
