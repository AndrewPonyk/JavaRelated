package com.example.pipeline.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.pipeline.TestEvents;
import com.example.pipeline.application.filter.ThresholdPredicate;
import com.example.pipeline.application.port.AggregateSink;
import com.example.pipeline.application.port.EventGenerator;
import com.example.pipeline.application.port.EventPredicate;
import com.example.pipeline.application.port.RateLimiter;
import com.example.pipeline.application.stage.AggregationStage;
import com.example.pipeline.application.stage.FilterStage;
import com.example.pipeline.application.stage.ProducerStage;
import com.example.pipeline.domain.AggregateResult;
import com.example.pipeline.domain.MetricsSnapshot;
import com.example.pipeline.domain.SensorEvent;
import com.example.pipeline.infrastructure.concurrent.PipelineExecutors;
import com.example.pipeline.infrastructure.metrics.AtomicMetricsRecorder;
import com.example.pipeline.infrastructure.persistence.InMemoryAggregateRepository;
import com.example.pipeline.infrastructure.queue.BoundedStageQueue;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * End-to-end tests for {@link PipelineOrchestrator} — the whole three-stage graph,
 * wired from real components: real {@link BoundedStageQueue}s, real
 * {@link PipelineExecutors} pools, a real {@code ForkJoinPool}.
 *
 * <p>Nothing here is mocked, on purpose. The properties worth testing at this level are
 * emergent: <em>every produced event is accounted for</em>, <em>the poison pill reaches
 * all three stages in the right order</em>, and <em>a stage failure comes back as a
 * report rather than as an exception</em>. All three involve the interaction between the
 * queues, the pools and the pill arithmetic; a test with a mock queue would assert on
 * the wiring and prove nothing about the behaviour.
 *
 * <p>The single most important assertion in the suite is
 * {@link MetricsSnapshot#reconciles()}: {@code produced == passed + rejected}. It is the
 * only check that can distinguish "the pipeline finished" from "the pipeline finished and
 * did not silently drop anything on the way", and a dropped event is exactly the failure
 * mode a bounded queue plus a poison pill is meant to make impossible.
 *
 * <p>Every test runs under {@code @Timeout}, because the natural failure of a pipeline
 * bug is a hang, not an assertion error. The reconciliation tests are
 * {@code @RepeatedTest} because a single green run of a concurrent shutdown proves very
 * little.
 */
class PipelineOrchestratorTest {

    private static final Duration POLL = Duration.ofMillis(50);
    private static final Duration SHUTDOWN = Duration.ofSeconds(15);
    private static final int SENSORS = 8;

    private Fixture fixture;

    @AfterEach
    void releasePools() {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        /**
         * The reference run: a tight queue so backpressure is genuinely exercised, a
         * threshold that splits the stream exactly in half, and arithmetic — not
         * hand-counted — expectations.
         *
         * <p>Values ascend {@code 0..4999}, so {@code value > 2499.0} passes exactly the
         * 2500 events {@code 2500..4999}. Any lost event shows up in three independent
         * places: the filter tally, the aggregate total and {@code reconciles()}.
         */
        @RepeatedTest(5)
        @Timeout(120)
        @DisplayName("every produced event is either passed or rejected, and the numbers agree everywhere")
        void reconcilesEveryEvent() {
            fixture = new Fixture()
                    .eventCount(5_000L)
                    .batchSize(64)
                    .consumerThreads(4)
                    // Capacity 4 against a batch size of 64 means the producer spends most
                    // of the run parked in put(). That is the state a lost-event bug needs.
                    .queueCapacity(4)
                    .predicate(new ThresholdPredicate(2_499.0));

            PipelineReport report = fixture.build().run();

            assertTrue(report.success(), report.message());
            assertEquals(PipelineReport.EXIT_OK, report.exitCode());

            MetricsSnapshot metrics = report.metrics();
            assertEquals(5_000L, metrics.eventsProduced());
            assertEquals(2_500L, metrics.eventsPassed());
            assertEquals(2_500L, metrics.eventsRejected());
            assertTrue(metrics.reconciles(), metrics.toLogLine());
            assertEquals(0L, report.lostEvents());
            assertEquals(0L, report.totalErrors());

            // The same 5000/2500 split seen through the per-stage tallies, which are
            // accumulated independently of the metrics recorder.
            assertEquals(5_000L, report.producer().eventsOut());
            assertEquals(5_000L, report.filter().eventsIn());
            assertEquals(2_500L, report.filter().eventsOut());
            assertEquals(2_500L, report.aggregation().eventsIn());
            // And through the aggregates, which are computed on the fork/join pool.
            assertEquals(2_500L, report.snapshot().totalCount());
        }

        @Test
        @Timeout(60)
        @DisplayName("an accept-all run aggregates every event, spread over every sensor")
        void acceptAllReachesEverySensor() {
            fixture = new Fixture()
                    .eventCount(1_024L)
                    .batchSize(32)
                    .consumerThreads(4)
                    .queueCapacity(8)
                    .predicate(EventPredicate.acceptAll());

            PipelineReport report = fixture.build().run();

            assertTrue(report.success(), report.message());
            assertEquals(0L, report.metrics().eventsRejected());
            assertEquals(1_024L, report.snapshot().totalCount());
            assertEquals(SENSORS, report.snapshot().sensorCount());
            // Sensors are assigned round-robin by sequence, so the split is exact. An
            // off-by-one in the batching would show up as an uneven distribution here.
            for (AggregateResult result : report.snapshot().sortedBySensorId()) {
                assertEquals(1_024L / SENSORS, result.count(), result.sensorId());
            }
            assertTrue(fixture.repository.findLatest().isPresent(), "the final snapshot must be stored");
        }

        /**
         * A run where nothing survives the filter still has to reconcile — the rejected
         * events are accounted for, not lost. This is also the case where stage 3 receives
         * only a poison pill and no batch at all.
         */
        @Test
        @Timeout(60)
        @DisplayName("a run that rejects everything still reconciles and still succeeds")
        void rejectsEverythingAndStillReconciles() {
            fixture = new Fixture()
                    .eventCount(512L)
                    .batchSize(16)
                    .consumerThreads(2)
                    .queueCapacity(4)
                    .predicate(new ThresholdPredicate(1e9));

            PipelineReport report = fixture.build().run();

            assertTrue(report.success(), report.message());
            assertEquals(512L, report.metrics().eventsRejected());
            assertEquals(0L, report.metrics().eventsPassed());
            assertEquals(0L, report.aggregation().eventsIn());
            assertTrue(report.snapshot().isEmpty());
            assertTrue(report.metrics().reconciles());
        }

        /**
         * A slow run that takes several times the shutdown budget end to end, and still
         * succeeds. How long a run may last is set by the event count and the duration
         * bound, never by the shutdown grace period.
         *
         * <p>Regression guard for a real defect: the drain budget was passed straight to
         * {@code allOf(...).get(timeout)}, which made it a deadline for the <em>whole
         * run</em>. The shipped defaults — 100 000 events at 10 000/s against a 10 s
         * budget — sat exactly on that boundary, so the first honest end-to-end run died
         * at ten seconds and printed {@code result : FAILED} directly above
         * {@code reconciled=true}, with an empty aggregate table for a pipeline that had
         * aggregated 48 015 events.
         *
         * <p>The rate limiter is what makes the run slow, so the assertion is on the
         * verdict rather than on any elapsed-time bound: a loaded runner makes this run
         * slower, which only makes the test a stronger guard.
         */
        @Test
        @Timeout(120)
        @DisplayName("a run far longer than the shutdown budget is not a stall")
        void outlivesTheShutdownBudget() {
            fixture = new Fixture()
                    .eventCount(400L)
                    .batchSize(10)
                    // 40 batches, ~50 ms apart, is ~2 s of production against a 1 s
                    // budget -- and every batch moves a counter, so nothing has stalled.
                    .rateLimiter(permits -> LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50)))
                    .shutdownTimeout(Duration.ofSeconds(1))
                    .consumerThreads(2)
                    .queueCapacity(4)
                    .predicate(EventPredicate.acceptAll());

            PipelineReport report = fixture.build().run();

            assertTrue(report.success(), report.message());
            assertEquals(PipelineReport.EXIT_OK, report.exitCode());
            assertEquals(400L, report.metrics().eventsProduced());
            assertEquals(400L, report.snapshot().totalCount());
            assertTrue(report.metrics().reconciles(), report.metrics().toLogLine());
        }

        @Test
        @Timeout(60)
        @DisplayName("the report renders without blowing up, including the aggregate rows")
        void describesItself() {
            fixture = new Fixture()
                    .eventCount(256L)
                    .batchSize(16)
                    .consumerThreads(2)
                    .predicate(EventPredicate.acceptAll());

            String described = fixture.build().run().describe();

            assertTrue(described.contains("result : OK"), described);
            assertTrue(described.contains("reconciled=true"), described);
            assertTrue(described.contains("sensor-0"), described);
        }
    }

    @Nested
    @DisplayName("graceful shutdown")
    class GracefulShutdown {

        /**
         * The {@code Ctrl+C} path that must still be lossless: the producer is stopped
         * mid-flight, emits its pills, and everything already queued is filtered and
         * aggregated before the stages exit.
         *
         * <p>The run is started on its own thread and the stop is triggered by a latch the
         * rate limiter counts down — not by a sleep. A sleep would either stop the
         * producer before it started (proving nothing) or make the test slow for no
         * reason, and on a loaded CI runner it would do both on different days.
         */
        @RepeatedTest(5)
        @Timeout(120)
        @DisplayName("stopping the producer mid-run drains the queues without losing an event")
        void stoppingTheProducerDrainsCleanly() throws Exception {
            CountDownLatch flowing = new CountDownLatch(8);
            fixture = new Fixture()
                    // No event budget: only the stop request ends this run.
                    .eventCount(0L)
                    .maxDuration(Duration.ofSeconds(60))
                    .batchSize(32)
                    .consumerThreads(4)
                    .queueCapacity(8)
                    .predicate(new ThresholdPredicate(0.0))
                    .rateLimiter(permits -> flowing.countDown());
            PipelineOrchestrator orchestrator = fixture.build();

            CompletableFuture<PipelineReport> running =
                    CompletableFuture.supplyAsync(orchestrator::run);
            assertTrue(flowing.await(30, TimeUnit.SECONDS), "the producer should have emitted some batches");

            // Straight at the stage, so this test stays honest about where the drain
            // comes from: the pills the producer emits on its way out, not anything the
            // orchestrator does. The test below drives the same thing through the
            // orchestrator's public entry point.
            fixture.producer.requestStop();
            PipelineReport report = running.get(60, TimeUnit.SECONDS);

            assertTrue(report.success(), report.message());
            assertTrue(report.metrics().eventsProduced() > 0L, "nothing was produced before the stop");
            assertTrue(report.metrics().reconciles(), report.metrics().toLogLine());
            assertEquals(0L, report.lostEvents());
            assertEquals(report.metrics().eventsPassed(), report.snapshot().totalCount(),
                    "every event that passed the filter must appear in an aggregate");
        }

        /**
         * The same drain reached through the orchestrator's public {@code Ctrl+C} entry
         * point, which is what the shutdown hook actually calls — so this is the test
         * that pins the interactive contract.
         *
         * <p>It is a regression guard as much as a feature test. When
         * {@code requestStopAll} stopped all three stages at once, the consumers exited
         * with batches still sitting in queue #1: those events were lost, the producer
         * wedged in {@code put()} against a queue nobody was draining any more, and the
         * run ended in a drain timeout and a forced shutdown — reporting failure for a
         * pipeline that was working perfectly. Hence the assertion on {@code success()}
         * rather than merely on termination.
         */
        @Test
        @Timeout(120)
        @DisplayName("requestStopAll drains through the pills and reports success")
        void requestStopAllDrainsCleanly() throws Exception {
            CountDownLatch flowing = new CountDownLatch(8);
            fixture = new Fixture()
                    .eventCount(0L)
                    .maxDuration(Duration.ofSeconds(60))
                    .batchSize(32)
                    .consumerThreads(4)
                    .queueCapacity(8)
                    .predicate(new ThresholdPredicate(0.0))
                    .rateLimiter(permits -> flowing.countDown());
            PipelineOrchestrator orchestrator = fixture.build();

            CompletableFuture<PipelineReport> running =
                    CompletableFuture.supplyAsync(orchestrator::run);
            assertTrue(flowing.await(30, TimeUnit.SECONDS));

            orchestrator.requestStopAll();
            // Idempotent and safe from any thread -- a shutdown hook may well call it
            // twice while run() is still blocked in get().
            orchestrator.requestStopAll();

            PipelineReport report = running.get(60, TimeUnit.SECONDS);

            assertNotNull(report);
            assertTrue(report.success(), report.message());
            assertEquals(PipelineReport.EXIT_OK, report.exitCode());

            MetricsSnapshot metrics = report.metrics();
            assertTrue(metrics.eventsProduced() > 0L, "nothing was produced before the stop");
            assertTrue(metrics.reconciles(), metrics.toLogLine());
            assertEquals(0L, report.lostEvents());
            assertEquals(metrics.eventsPassed(), report.snapshot().totalCount());
        }
    }

    @Nested
    @DisplayName("failure handling")
    class Failures {

        /**
         * A stage that throws must come back as {@code success=false} plus the partial
         * numbers, not as an exception out of {@code run()}. The caller needs the counters
         * to work out where the run died; an exception would discard them.
         *
         * <p>The event count is deliberately small enough to fit inside queue #1, so the
         * producer completes rather than parking forever against consumers that have
         * already died — that would be a drain timeout, which is the next test.
         */
        @Test
        @Timeout(60)
        @DisplayName("a throwing predicate is reported, not thrown")
        void aStageFailureIsReported() {
            fixture = new Fixture()
                    .eventCount(4L)
                    .batchSize(1)
                    .consumerThreads(2)
                    .queueCapacity(16)
                    .predicate(new ExplodingPredicate());

            PipelineReport report = fixture.build().run();

            assertFalse(report.success());
            assertTrue(report.message().contains("stage failed"), report.message());
            assertTrue(report.message().contains("IllegalStateException"), report.message());
            assertEquals(PipelineReport.EXIT_FAILURE, report.exitCode());
            // The producer still finished, and its numbers are still in the report.
            assertEquals(4L, report.metrics().eventsProduced());
        }

        /**
         * A consumer wedged inside the predicate. Every counter stops moving — the
         * consumers are stuck, so the queue fills, so the producer parks in {@code put()}
         * — and the orchestrator must notice the lost liveness, escalate, and report it.
         * A run that needed {@code forceShutdown()} is a failed run even if the numbers
         * happen to look plausible.
         */
        @Test
        @Timeout(120)
        @DisplayName("a wedged pipeline is reported as a stall")
        void aStallIsReported() {
            fixture = new Fixture()
                    .eventCount(512L)
                    .batchSize(16)
                    .consumerThreads(2)
                    .queueCapacity(2)
                    .shutdownTimeout(Duration.ofSeconds(1))
                    .predicate(new ParkingPredicate());

            PipelineReport report = fixture.build().run();

            assertFalse(report.success());
            assertTrue(report.message().contains("stalled"), report.message());
            assertNotEquals(PipelineReport.EXIT_OK, report.exitCode());
        }

        @Test
        @DisplayName("rejects nulls and a non-positive shutdown timeout")
        void rejectsBadArguments() {
            fixture = new Fixture();
            fixture.build();

            assertThrows(NullPointerException.class, () -> new PipelineOrchestrator(
                    null, fixture.filter, fixture.aggregation, fixture.executors, fixture.metrics, SHUTDOWN));
            assertThrows(NullPointerException.class, () -> new PipelineOrchestrator(
                    fixture.producer, fixture.filter, fixture.aggregation, null, fixture.metrics, SHUTDOWN));
            // A zero budget would mean "escalate immediately", which turns every run into
            // a forced shutdown and every report into a failure.
            assertThrows(IllegalArgumentException.class, () -> new PipelineOrchestrator(
                    fixture.producer, fixture.filter, fixture.aggregation, fixture.executors,
                    fixture.metrics, Duration.ZERO));
            assertThrows(IllegalArgumentException.class, () -> new PipelineOrchestrator(
                    fixture.producer, fixture.filter, fixture.aggregation, fixture.executors,
                    fixture.metrics, Duration.ofSeconds(-1)));
        }
    }

    /**
     * Wiring for one run.
     *
     * <p>A small builder rather than a nine-argument helper method: each test overrides
     * the two or three settings it actually cares about, and the rest of its setup stays
     * out of the way of its point.
     */
    private static final class Fixture {

        private final AtomicMetricsRecorder metrics = new AtomicMetricsRecorder();
        private final InMemoryAggregateRepository repository = new InMemoryAggregateRepository();

        private long eventCount = 1_000L;
        private Duration maxDuration = Duration.ZERO;
        private int batchSize = 32;
        private int consumerThreads = 2;
        private int queueCapacity = 8;
        private Duration shutdownTimeout = SHUTDOWN;
        private EventPredicate predicate = EventPredicate.acceptAll();
        private RateLimiter rateLimiter = RateLimiter.unlimited();

        private PipelineExecutors executors;
        private ProducerStage producer;
        private FilterStage filter;
        private AggregationStage aggregation;

        private Fixture eventCount(long value) {
            this.eventCount = value;
            return this;
        }

        private Fixture maxDuration(Duration value) {
            this.maxDuration = value;
            return this;
        }

        private Fixture batchSize(int value) {
            this.batchSize = value;
            return this;
        }

        private Fixture consumerThreads(int value) {
            this.consumerThreads = value;
            return this;
        }

        private Fixture queueCapacity(int value) {
            this.queueCapacity = value;
            return this;
        }

        private Fixture shutdownTimeout(Duration value) {
            this.shutdownTimeout = value;
            return this;
        }

        private Fixture predicate(EventPredicate value) {
            this.predicate = value;
            return this;
        }

        private Fixture rateLimiter(RateLimiter value) {
            this.rateLimiter = value;
            return this;
        }

        private PipelineOrchestrator build() {
            executors = new PipelineExecutors(consumerThreads, 2);
            BoundedStageQueue toFilter = new BoundedStageQueue("producer->filter", queueCapacity, metrics);
            BoundedStageQueue toAggregation = new BoundedStageQueue("filter->aggregate", queueCapacity, metrics);

            filter = new FilterStage(toFilter, toAggregation, predicate, metrics,
                    executors.consumerExecutor(), consumerThreads, POLL);
            producer = new ProducerStage(ASCENDING, rateLimiter, toFilter, metrics, batchSize,
                    eventCount, maxDuration, filter.consumerCount(), executors.producerExecutor());
            aggregation = new AggregationStage(toAggregation, executors.forkJoinPool(),
                    executors.dispatcherExecutor(), AggregateSink.discarding(), repository, metrics,
                    64, POLL);

            return new PipelineOrchestrator(producer, filter, aggregation, executors, metrics, shutdownTimeout);
        }

        private void close() {
            if (executors != null) {
                executors.close();
            }
        }
    }

    /**
     * Ascending values over {@link #SENSORS} sensors, so a threshold of {@code t} passes
     * exactly the events above it and the expected counts stay arithmetic.
     */
    private static final EventGenerator ASCENDING =
            sequence -> TestEvents.event(sequence, "sensor-" + (sequence % SENSORS), sequence);

    /** Predicate that always throws — a bug in the rule, which must surface as a failed run. */
    private static final class ExplodingPredicate implements EventPredicate {
        @Override
        public boolean test(SensorEvent event) {
            throw new IllegalStateException("predicate blew up");
        }

        @Override
        public String description() {
            return "always throws";
        }
    }

    /**
     * Predicate that blocks until its thread is interrupted — a stand-in for the real
     * cause of a drain overrun (a wedged IO call, a lock nobody releases).
     *
     * <p>Interruptible on purpose: an uninterruptible block would leave threads alive
     * after the test method returned and poison every test that followed.
     */
    private static final class ParkingPredicate implements EventPredicate {
        @Override
        public boolean test(SensorEvent event) {
            while (!Thread.currentThread().isInterrupted()) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
            }
            return false;
        }

        @Override
        public String description() {
            return "blocks until interrupted";
        }
    }
}
