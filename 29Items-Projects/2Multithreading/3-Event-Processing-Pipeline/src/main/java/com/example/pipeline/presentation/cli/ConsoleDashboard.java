package com.example.pipeline.presentation.cli;

import com.example.pipeline.application.port.MessageChannel;
import com.example.pipeline.application.port.MetricsRecorder;
import com.example.pipeline.domain.AggregateSnapshot;
import com.example.pipeline.domain.MetricsSnapshot;
import com.example.pipeline.infrastructure.concurrent.NamedThreadFactory;
import java.io.PrintStream;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The user-facing view: a periodically refreshed one-line status of the running pipeline.
 *
 * <p><strong>This is the "frontend component" for this stack.</strong> The project is a
 * local console application with no HTTP UI, so the presentation layer is a terminal
 * renderer. It obeys the same rules a UI component would:
 * <ul>
 *   <li><strong>loading state</strong> — before the first batch is aggregated it prints
 *       {@code starting...} rather than a misleading row of zeros;</li>
 *   <li><strong>data state</strong> — throughput, queue depths and pass rate, refreshed
 *       every {@code pipeline.dashboard.interval.millis};</li>
 *   <li><strong>error state</strong> — a non-zero error count is rendered inline as
 *       {@code errors=N}, so a failing run is visible without reading the log;</li>
 *   <li><strong>it never mutates anything</strong> — every value comes from an immutable
 *       snapshot or an atomic counter read.</li>
 * </ul>
 *
 * <p><strong>Why its own daemon thread.</strong> Rendering on a pipeline thread would make
 * console I/O — which blocks, and blocks for a long time when the terminal is a slow SSH
 * session — part of the data path. A single-threaded scheduled executor keeps that cost
 * off the stages entirely, and the thread is a <em>daemon</em> so a forgotten
 * {@link #stop()} can never keep the JVM alive.
 *
 * <p><strong>Why the numbers may not add up mid-run, and why that is fine.</strong> The
 * counters are independent {@code LongAdder}s read without a lock, so a refresh can catch
 * the pipeline between "filter passed a batch" and "aggregator counted it". The dashboard
 * is a progress indicator, not a ledger — the reconciliation check that must be exact runs
 * once, after every stage has terminated, in {@code PipelineOrchestrator}.
 *
 * <p><strong>ASCII only, one line per refresh, no cursor control.</strong> No ANSI escapes
 * and no in-place redraw: the output has to stay readable when piped to a file or captured
 * by CI, where {@code \r}-overwriting produces an unreadable single line.
 */
public final class ConsoleDashboard implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(ConsoleDashboard.class.getName());

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final MetricsRecorder metrics;
    private final Supplier<AggregateSnapshot> snapshotSupplier;
    private final List<MessageChannel> channels;
    private final Duration interval;
    private final PrintStream out;
    private final ScheduledExecutorService scheduler;
    private final long startNanos = System.nanoTime();
    private volatile boolean started;

    /**
     * @param metrics          counter source
     * @param snapshotSupplier live aggregates, typically {@code AggregationStage::currentSnapshot}
     * @param channels         queues whose depth is displayed, in pipeline order
     * @param interval         refresh interval; must be positive
     */
    public ConsoleDashboard(MetricsRecorder metrics, Supplier<AggregateSnapshot> snapshotSupplier,
                            List<MessageChannel> channels, Duration interval) {
        this(metrics, snapshotSupplier, channels, interval, System.out);
    }

    /**
     * @param metrics          counter source
     * @param snapshotSupplier live aggregates
     * @param channels         queues whose depth is displayed
     * @param interval         refresh interval; must be positive
     * @param out              destination; injected so tests can assert on the rendering
     */
    public ConsoleDashboard(MetricsRecorder metrics, Supplier<AggregateSnapshot> snapshotSupplier,
                            List<MessageChannel> channels, Duration interval, PrintStream out) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.channels = List.copyOf(Objects.requireNonNull(channels, "channels"));
        this.interval = Objects.requireNonNull(interval, "interval");
        this.out = Objects.requireNonNull(out, "out");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("interval must be positive but was " + interval);
        }
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                new NamedThreadFactory("pipeline-dashboard", true));
    }

    /** Starts refreshing. Idempotent. */
    public void start() {
        if (started) {
            return;
        }
        started = true;
        long millis = interval.toMillis();
        // scheduleAtFixedRate, not scheduleWithFixedDelay: the refresh is a status tick, so
        // a slow console should not stretch the interval for every later tick. A run of
        // late ticks is preferable to silently drifting to a longer period.
        scheduler.scheduleAtFixedRate(this::renderSafely, millis, millis, TimeUnit.MILLISECONDS);
        out.println("pipeline starting...");
        out.flush();
    }

    /** Stops refreshing and prints one final line. Idempotent, and safe from a shutdown hook. */
    public void stop() {
        if (!started) {
            scheduler.shutdownNow();
            return;
        }
        started = false;
        scheduler.shutdownNow();
        renderSafely();
    }

    /** Renders one line immediately — useful in tests and for a final frame. */
    public void renderOnce() {
        out.println(render());
        out.flush();
    }

    /**
     * The current status line.
     *
     * @return an ASCII line; never {@code null}
     */
    public String render() {
        MetricsSnapshot snapshot = metrics.snapshot();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        if (snapshot.eventsProduced() == 0L) {
            // Loading state: zeros here would look like a stalled pipeline rather than one
            // that has not produced its first batch yet.
            return String.format(Locale.ROOT, "[%5.1fs] starting... %s", seconds(elapsed), queueSummary());
        }
        AggregateSnapshot aggregates = snapshotSupplier.get();
        double throughput = throughputPerSecond(snapshot.eventsProduced(), elapsed);
        double passRate = passRate(snapshot);
        StringBuilder line = new StringBuilder(160);
        line.append(String.format(Locale.ROOT,
                "[%5.1fs] produced=%d (%.0f/s) passed=%d (%.1f%%) rejected=%d aggregated=%d sensors=%d %s",
                seconds(elapsed), snapshot.eventsProduced(), throughput, snapshot.eventsPassed(), passRate,
                snapshot.eventsRejected(), snapshot.eventsAggregated(), aggregates.sensorCount(), queueSummary()));
        if (snapshot.errors() > 0L) {
            // Error state, inline: a failing run must be visible in the view itself.
            line.append(" errors=").append(snapshot.errors());
        }
        return line.toString();
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * Renders, swallowing anything thrown.
     *
     * <p>A {@code scheduleAtFixedRate} task that throws is <em>silently cancelled</em> —
     * the exception goes into the {@code ScheduledFuture} nobody holds, and the dashboard
     * simply stops updating with no explanation. Catching here keeps a display bug from
     * looking like a hung pipeline.
     */
    private void renderSafely() {
        try {
            renderOnce();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "dashboard refresh failed; display may be stale", e);
        }
    }

    private String queueSummary() {
        StringBuilder summary = new StringBuilder(48);
        for (MessageChannel channel : channels) {
            if (summary.length() > 0) {
                summary.append(' ');
            }
            summary.append(channel.name()).append('=')
                    .append(channel.depth()).append('/').append(channel.capacity());
        }
        return summary.toString();
    }

    private static double seconds(Duration elapsed) {
        return elapsed.toNanos() / (double) NANOS_PER_SECOND;
    }

    private static double throughputPerSecond(long events, Duration elapsed) {
        long nanos = elapsed.toNanos();
        return nanos <= 0L ? 0.0d : events * (double) NANOS_PER_SECOND / nanos;
    }

    private static double passRate(MetricsSnapshot snapshot) {
        long filtered = snapshot.eventsFiltered();
        return filtered == 0L ? 0.0d : 100.0d * snapshot.eventsPassed() / filtered;
    }
}
