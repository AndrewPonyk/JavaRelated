package com.example.pipeline.presentation.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.pipeline.application.port.MessageChannel;
import com.example.pipeline.domain.AggregateResult;
import com.example.pipeline.domain.AggregateSnapshot;
import com.example.pipeline.domain.PoisonPill;
import com.example.pipeline.infrastructure.metrics.AtomicMetricsRecorder;
import com.example.pipeline.infrastructure.queue.BoundedStageQueue;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Rendering and lifecycle of the terminal view.
 *
 * <p><strong>No test here waits for a scheduled refresh.</strong> The interval is set to an
 * hour so the timer never fires during a test, and every rendering assertion goes through
 * {@link ConsoleDashboard#render()} or {@link ConsoleDashboard#renderOnce()} directly. The
 * one thing that genuinely needs the scheduled path — that a throwing snapshot supplier
 * cannot silently cancel the refresh task — is reached through {@code stop()}, which renders
 * a final frame using the same swallowing wrapper the timer uses.
 *
 * <p>The {@link PrintStream} is injected, which is the whole reason the second constructor
 * exists: asserting on captured bytes beats asserting that {@code System.out} was touched.
 */
@Timeout(20)
@DisplayName("ConsoleDashboard")
class ConsoleDashboardTest {

    /** Long enough that no scheduled tick can fire inside a test. */
    private static final Duration NEVER = Duration.ofHours(1);

    private AtomicMetricsRecorder metrics;
    private AtomicReference<AggregateSnapshot> snapshot;
    private List<MessageChannel> channels;
    private ByteArrayOutputStream captured;
    private PrintStream out;

    @BeforeEach
    void setUp() {
        metrics = new AtomicMetricsRecorder();
        snapshot = new AtomicReference<>(AggregateSnapshot.empty());
        channels = List.of(new BoundedStageQueue("raw", 16, metrics),
                new BoundedStageQueue("filtered", 8, metrics));
        captured = new ByteArrayOutputStream();
        out = new PrintStream(captured, true, StandardCharsets.UTF_8);
    }

    private ConsoleDashboard dashboard() {
        return new ConsoleDashboard(metrics, snapshot::get, channels, NEVER, out);
    }

    private String printed() {
        out.flush();
        return captured.toString(StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("null collaborators are rejected")
        void nullsAreRejected() {
            assertThrows(NullPointerException.class,
                    () -> new ConsoleDashboard(null, snapshot::get, channels, NEVER, out));
            assertThrows(NullPointerException.class,
                    () -> new ConsoleDashboard(metrics, null, channels, NEVER, out));
            assertThrows(NullPointerException.class,
                    () -> new ConsoleDashboard(metrics, snapshot::get, null, NEVER, out));
            assertThrows(NullPointerException.class,
                    () -> new ConsoleDashboard(metrics, snapshot::get, channels, null, out));
            assertThrows(NullPointerException.class,
                    () -> new ConsoleDashboard(metrics, snapshot::get, channels, NEVER, null));
        }

        @Test
        @DisplayName("a zero or negative interval is rejected - it would spin the scheduler")
        void nonPositiveIntervalIsRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ConsoleDashboard(metrics, snapshot::get, channels, Duration.ZERO, out));
            assertThrows(IllegalArgumentException.class,
                    () -> new ConsoleDashboard(metrics, snapshot::get, channels, Duration.ofMillis(-1), out));
        }

        @Test
        @DisplayName("the channel list is copied, so a later mutation cannot change the view")
        void channelListIsCopied() {
            List<MessageChannel> mutable = new java.util.ArrayList<>(channels);
            ConsoleDashboard dashboard = new ConsoleDashboard(metrics, snapshot::get, mutable, NEVER, out);
            mutable.clear();
            assertTrue(dashboard.render().contains("raw=0/16"), dashboard.render());
        }

        @Test
        @DisplayName("the System.out constructor is usable without an explicit stream")
        void defaultConstructorWorks() {
            try (ConsoleDashboard dashboard =
                         new ConsoleDashboard(metrics, snapshot::get, channels, NEVER)) {
                assertTrue(dashboard.render().startsWith("["));
            }
        }
    }

    @Nested
    @DisplayName("rendering")
    class Rendering {

        @Test
        @DisplayName("before the first event it shows a loading state, not a row of zeros")
        void loadingStateBeforeFirstEvent() {
            String line = dashboard().render();
            assertTrue(line.contains("starting..."), line);
            assertFalse(line.contains("produced="), "zeros would read as a stalled pipeline: " + line);
        }

        @Test
        @DisplayName("the loading state still shows queue depths")
        void loadingStateIncludesQueues() {
            String line = dashboard().render();
            assertTrue(line.contains("raw=0/16"), line);
            assertTrue(line.contains("filtered=0/8"), line);
        }

        @Test
        @DisplayName("once events flow it shows counters, pass rate and sensor count")
        void dataStateShowsCounters() {
            metrics.recordProduced(100L, 4L);
            metrics.recordFiltered(75L, 25L);
            metrics.recordAggregated(75L, 3L);
            snapshot.set(new AggregateSnapshot(Map.of(
                    "sensor-a", new AggregateResult("sensor-a", 40L, 1.0, 9.0, 100.0),
                    "sensor-b", new AggregateResult("sensor-b", 35L, 2.0, 8.0, 90.0))));

            String line = dashboard().render();
            assertTrue(line.contains("produced=100"), line);
            assertTrue(line.contains("passed=75"), line);
            assertTrue(line.contains("rejected=25"), line);
            assertTrue(line.contains("aggregated=75"), line);
            assertTrue(line.contains("sensors=2"), line);
            // 75 of 100 filtered events passed; the rate is over eventsFiltered, not over
            // eventsProduced, so an in-flight batch cannot push it over 100%.
            assertTrue(line.contains("(75.0%)"), line);
        }

        @Test
        @DisplayName("a non-zero error count is rendered inline")
        void errorStateIsVisibleInTheView() {
            metrics.recordProduced(10L, 1L);
            metrics.recordError("filter");
            metrics.recordError("aggregation");
            String line = dashboard().render();
            assertTrue(line.endsWith("errors=2"), line);
        }

        @Test
        @DisplayName("no errors means no errors= noise")
        void healthyRunOmitsTheErrorField() {
            metrics.recordProduced(10L, 1L);
            assertFalse(dashboard().render().contains("errors="), dashboard().render());
        }

        @Test
        @DisplayName("a queue's live depth is reflected")
        void queueDepthIsLive() throws Exception {
            BoundedStageQueue raw = new BoundedStageQueue("raw", 4, metrics);
            // Any message occupies a slot; pills are the cheapest thing to enqueue and the
            // dashboard reads depth(), not the contents.
            raw.put(PoisonPill.INSTANCE);
            raw.put(PoisonPill.INSTANCE);
            ConsoleDashboard dashboard =
                    new ConsoleDashboard(metrics, snapshot::get, List.of(raw), NEVER, out);
            assertTrue(dashboard.render().contains("raw=2/4"), dashboard.render());
        }

        @Test
        @DisplayName("the line is ASCII only, so a piped log stays readable")
        void renderIsAsciiOnly() {
            metrics.recordProduced(10L, 1L);
            metrics.recordFiltered(5L, 5L);
            String line = dashboard().render();
            for (int i = 0; i < line.length(); i++) {
                assertTrue(line.charAt(i) < 0x80,
                        "non-ASCII U+" + Integer.toHexString(line.charAt(i)) + " in: " + line);
            }
            assertFalse(line.indexOf(0x1b) >= 0, "no ANSI escapes: " + line);
            assertFalse(line.contains("\r"), "no cursor control: " + line);
        }

        @Test
        @DisplayName("renderOnce writes exactly one line")
        void renderOnceWritesOneLine() {
            dashboard().renderOnce();
            assertEquals(1, printed().lines().count(), printed());
        }

        @Test
        @DisplayName("a division by zero cannot happen when nothing has been filtered")
        void zeroFilteredDoesNotProduceNaN() {
            metrics.recordProduced(10L, 1L);
            String line = dashboard().render();
            assertTrue(line.contains("(0.0%)"), line);
            assertFalse(line.contains("NaN"), line);
        }
    }

    @Nested
    @DisplayName("lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("start announces itself and is idempotent")
        void startIsIdempotent() {
            try (ConsoleDashboard dashboard = dashboard()) {
                dashboard.start();
                dashboard.start();
                assertEquals(1, printed().lines().filter(l -> l.contains("pipeline starting...")).count(),
                        printed());
            }
        }

        @Test
        @DisplayName("stop prints one final frame")
        void stopPrintsAFinalFrame() {
            ConsoleDashboard dashboard = dashboard();
            dashboard.start();
            dashboard.stop();
            List<String> lines = printed().lines().toList();
            assertEquals(2, lines.size(), printed());
            assertTrue(lines.get(1).contains("starting..."), lines.get(1));
        }

        @Test
        @DisplayName("stop is idempotent and does not print twice")
        void stopIsIdempotent() {
            ConsoleDashboard dashboard = dashboard();
            dashboard.start();
            dashboard.stop();
            dashboard.stop();
            assertEquals(2, printed().lines().count(), printed());
        }

        @Test
        @DisplayName("stopping a dashboard that never started prints nothing and still releases the thread")
        void stoppingAnUnstartedDashboardIsSafe() {
            ConsoleDashboard dashboard = dashboard();
            dashboard.close();
            assertEquals("", printed());
        }

        @Test
        @DisplayName("close delegates to stop")
        void closeDelegatesToStop() {
            ConsoleDashboard dashboard = dashboard();
            dashboard.start();
            dashboard.close();
            assertEquals(2, printed().lines().count(), printed());
        }

        /**
         * A {@code scheduleAtFixedRate} task that throws is cancelled silently — the
         * exception lands in a {@code ScheduledFuture} nobody holds and the display simply
         * freezes, which reads as a hung pipeline. The wrapper that prevents this is also
         * used for the final frame, so stopping a dashboard whose supplier throws is the
         * timing-free way to prove the exception is contained.
         */
        @Test
        @DisplayName("a throwing snapshot supplier cannot escape the refresh path")
        void refreshFailureIsContained() {
            ConsoleDashboard dashboard = new ConsoleDashboard(metrics, () -> {
                throw new IllegalStateException("display bug");
            }, channels, NEVER, out);
            metrics.recordProduced(1L, 1L); // Force the data-state branch, which reads the supplier.
            dashboard.start();
            assertDoesNotThrow(dashboard::stop, "a display bug must not become a pipeline failure");
        }

        @Test
        @DisplayName("render itself still propagates, so a test cannot miss a broken view")
        void renderDoesNotSwallow() {
            ConsoleDashboard dashboard = new ConsoleDashboard(metrics, () -> {
                throw new IllegalStateException("display bug");
            }, channels, NEVER, out);
            metrics.recordProduced(1L, 1L);
            assertThrows(IllegalStateException.class, dashboard::render);
        }
    }
}
