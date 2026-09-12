package com.example.pipeline.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.pipeline.domain.AggregateResult;
import com.example.pipeline.domain.AggregateSnapshot;
import com.example.pipeline.domain.MetricsSnapshot;
import com.example.pipeline.domain.StageStats;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The value {@code main} turns into a process exit status.
 *
 * <p>The exit-code mapping is the part with real consequences, and
 * {@link ExitCodes#interruptedRunReports130()} is the only test in the suite that has to run
 * on a thread it owns: {@link PipelineReport#exitCode()} reads
 * {@code Thread.currentThread().isInterrupted()}, and setting that flag on the JUnit thread
 * would leak into every test that ran afterwards in the same worker.
 *
 * <p>{@link Description#describeIsAsciiOnly()} pins the same contract the console sink has —
 * this text is printed to a Windows terminal, where a stray non-ASCII character becomes
 * mojibake and reads as a corrupted report.
 */
@Timeout(20)
@DisplayName("PipelineReport")
class PipelineReportTest {

    private static final Duration ELAPSED = Duration.ofMillis(1234L);

    private static StageStats stats(String stage, long in, long out, long rejected, long errors) {
        return new StageStats(stage, 1L, in, out, rejected, errors, Duration.ofMillis(500L));
    }

    private static MetricsSnapshot metrics(long produced, long passed, long rejected) {
        return new MetricsSnapshot(produced, 1L, passed, rejected, passed, 1L, 0L,
                Map.of("raw", 1_000_000L));
    }

    private static AggregateSnapshot snapshot() {
        return new AggregateSnapshot(Map.of(
                "sensor-a", new AggregateResult("sensor-a", 6L, 1.0, 9.0, 30.0),
                "sensor-b", new AggregateResult("sensor-b", 4L, 2.0, 8.0, 20.0)));
    }

    private static PipelineReport ok() {
        return new PipelineReport(true, "all stages drained", stats("producer", 10L, 10L, 0L, 0L),
                stats("filter", 10L, 6L, 4L, 0L), stats("aggregation", 6L, 6L, 0L, 0L),
                snapshot(), metrics(10L, 6L, 4L), ELAPSED);
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("every component is required, even for a failed run")
        void nullsAreRejected() {
            StageStats empty = StageStats.empty("s");
            AggregateSnapshot snap = AggregateSnapshot.empty();
            MetricsSnapshot met = MetricsSnapshot.empty();
            assertThrows(NullPointerException.class,
                    () -> new PipelineReport(false, null, empty, empty, empty, snap, met, ELAPSED));
            assertThrows(NullPointerException.class,
                    () -> new PipelineReport(false, "m", null, empty, empty, snap, met, ELAPSED));
            assertThrows(NullPointerException.class,
                    () -> new PipelineReport(false, "m", empty, null, empty, snap, met, ELAPSED));
            assertThrows(NullPointerException.class,
                    () -> new PipelineReport(false, "m", empty, empty, null, snap, met, ELAPSED));
            assertThrows(NullPointerException.class,
                    () -> new PipelineReport(false, "m", empty, empty, empty, null, met, ELAPSED));
            assertThrows(NullPointerException.class,
                    () -> new PipelineReport(false, "m", empty, empty, empty, snap, null, ELAPSED));
            assertThrows(NullPointerException.class,
                    () -> new PipelineReport(false, "m", empty, empty, empty, snap, met, null));
        }

        @Test
        @DisplayName("failed() fills in zeroed stats rather than leaving holes")
        void failedFactoryIsFullyPopulated() {
            PipelineReport report = PipelineReport.failed("queue never drained", ELAPSED);
            assertFalse(report.success());
            assertEquals("queue never drained", report.message());
            // A caller that renders the report must not have to null-check any component,
            // which is why failed() builds empty values instead of leaving fields unset.
            assertEquals("producer", report.producer().stage());
            assertEquals("filter", report.filter().stage());
            assertEquals("aggregation", report.aggregation().stage());
            assertTrue(report.snapshot().isEmpty());
            assertEquals(0L, report.totalErrors());
            assertEquals(ELAPSED, report.elapsed());
        }
    }

    @Nested
    @DisplayName("derived values")
    class DerivedValues {

        @Test
        @DisplayName("totalErrors sums all three stages, not just the one that failed")
        void totalErrorsSpansEveryStage() {
            PipelineReport report = new PipelineReport(false, "errors", stats("producer", 10L, 10L, 0L, 1L),
                    stats("filter", 10L, 6L, 4L, 2L), stats("aggregation", 6L, 6L, 0L, 4L),
                    snapshot(), metrics(10L, 6L, 4L), ELAPSED);
            assertEquals(7L, report.totalErrors());
        }

        @Test
        @DisplayName("a clean run reports no errors")
        void healthyRunHasNoErrors() {
            assertEquals(0L, ok().totalErrors());
        }

        @Test
        @DisplayName("lostEvents is zero when the counters reconcile")
        void reconciledRunLosesNothing() {
            assertEquals(0L, ok().lostEvents());
        }

        @Test
        @DisplayName("lostEvents surfaces produced events the filter never accounted for")
        void unreconciledRunReportsTheGap() {
            // 10 produced, 6 + 3 = 9 decided: one event vanished. That is a correctness bug,
            // and the report has to name the number rather than merely say FAILED.
            PipelineReport report = new PipelineReport(false, "events lost",
                    stats("producer", 10L, 10L, 0L, 0L), stats("filter", 9L, 6L, 3L, 0L),
                    stats("aggregation", 6L, 6L, 0L, 0L), snapshot(), metrics(10L, 6L, 3L), ELAPSED);
            assertEquals(1L, report.lostEvents());
        }
    }

    @Nested
    @DisplayName("exit codes")
    class ExitCodes {

        @Test
        @DisplayName("the documented constants keep their values - scripts depend on them")
        void constantsAreStable() {
            assertEquals(0, PipelineReport.EXIT_OK);
            assertEquals(1, PipelineReport.EXIT_FAILURE);
            assertEquals(2, PipelineReport.EXIT_CONFIG);
            // 130 is the shell's SIGINT convention (128 + 2), not an arbitrary number.
            assertEquals(130, PipelineReport.EXIT_INTERRUPTED);
        }

        @Test
        @DisplayName("a successful run exits 0")
        void successfulRunExitsZero() {
            assertEquals(PipelineReport.EXIT_OK, ok().exitCode());
        }

        @Test
        @DisplayName("a failed run on an uninterrupted thread exits 1")
        void failedRunExitsOne() {
            assertFalse(Thread.currentThread().isInterrupted(), "precondition: a clean thread");
            assertEquals(PipelineReport.EXIT_FAILURE,
                    PipelineReport.failed("stage threw", ELAPSED).exitCode());
        }

        /**
         * Run on a dedicated thread: {@code exitCode()} reads the interrupt flag of whatever
         * thread calls it, and interrupting the JUnit worker would corrupt every later test
         * in the same JVM — surefire reuses one.
         */
        @Test
        @DisplayName("a failed run on an interrupted thread exits 130, not 1")
        void interruptedRunReports130() throws Exception {
            AtomicInteger observed = new AtomicInteger(-1);
            CountDownLatch done = new CountDownLatch(1);
            Thread worker = new Thread(() -> {
                Thread.currentThread().interrupt();
                observed.set(PipelineReport.failed("interrupted after a clean drain", ELAPSED).exitCode());
                done.countDown();
            }, "exit-code-probe");
            worker.setDaemon(true);
            worker.start();

            assertTrue(done.await(5, TimeUnit.SECONDS), "the probe thread should finish at once");
            // An operator pressing Ctrl+C during a soak run must not look like a defect.
            assertEquals(PipelineReport.EXIT_INTERRUPTED, observed.get());
            assertFalse(Thread.currentThread().isInterrupted(), "the test thread must be left clean");
        }

        @Test
        @DisplayName("success wins over the interrupt flag - a drained run is still a good run")
        void successIgnoresTheInterruptFlag() throws Exception {
            AtomicInteger observed = new AtomicInteger(-1);
            CountDownLatch done = new CountDownLatch(1);
            Thread worker = new Thread(() -> {
                Thread.currentThread().interrupt();
                observed.set(ok().exitCode());
                done.countDown();
            }, "exit-code-probe-ok");
            worker.setDaemon(true);
            worker.start();

            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals(PipelineReport.EXIT_OK, observed.get());
        }
    }

    @Nested
    @DisplayName("describe()")
    class Description {

        @Test
        @DisplayName("the text carries the outcome, the reason, the elapsed time and every stage line")
        void describeCoversTheWholeRun() {
            String text = ok().describe();
            assertTrue(text.contains("=== Pipeline report ==="), text);
            assertTrue(text.contains("result : OK"), text);
            assertTrue(text.contains("reason : all stages drained"), text);
            assertTrue(text.contains("elapsed: 1234 ms"), text);
            assertTrue(text.contains("stage=producer"), text);
            assertTrue(text.contains("stage=filter"), text);
            assertTrue(text.contains("stage=aggregation"), text);
            assertTrue(text.contains("reconciled=true"), text);
        }

        @Test
        @DisplayName("a failed run says FAILED, so grepping the log is enough")
        void failedRunIsLabelled() {
            String text = PipelineReport.failed("aggregation pool did not terminate", ELAPSED).describe();
            assertTrue(text.contains("result : FAILED"), text);
            assertTrue(text.contains("reason : aggregation pool did not terminate"), text);
        }

        @Test
        @DisplayName("one row per sensor, sorted, under a header naming the count")
        void describeListsEverySensor() {
            String text = ok().describe();
            assertTrue(text.contains("--- aggregates (2 sensors) ---"), text);
            int a = text.indexOf("sensor-a");
            int b = text.indexOf("sensor-b");
            assertTrue(a > 0 && a < b, "sorted output keeps two runs diffable: " + text);
        }

        @Test
        @DisplayName("a run with no aggregates still renders a well-formed report")
        void emptySnapshotStillDescribes() {
            String text = PipelineReport.failed("nothing ran", Duration.ZERO).describe();
            assertTrue(text.contains("--- aggregates (0 sensors) ---"), text);
            assertTrue(text.contains("elapsed: 0 ms"), text);
        }

        @Test
        @DisplayName("the report is ASCII only, so a Windows console renders it")
        void describeIsAsciiOnly() {
            String text = ok().describe();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                assertTrue(c < 0x80, "non-ASCII U+" + Integer.toHexString(c) + " in: " + text);
            }
        }

        @Test
        @DisplayName("every line is terminated, so the next log line starts at column zero")
        void describeEndsWithALineSeparator() {
            assertTrue(ok().describe().endsWith(System.lineSeparator()), ok().describe());
        }
    }
}
