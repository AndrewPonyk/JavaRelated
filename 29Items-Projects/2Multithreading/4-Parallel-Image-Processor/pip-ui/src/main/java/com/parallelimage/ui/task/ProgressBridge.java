package com.parallelimage.ui.task;

import com.parallelimage.core.progress.ProgressEvent;
import com.parallelimage.core.progress.ProgressListener;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;

/**
 * The one legal crossing between fork/join workers and the JavaFX application thread.
 *
 * <h2>The problem this solves</h2>
 * A batch of 4 000 images raises on the order of 8 000 {@link ProgressEvent}s from N worker threads.
 * The obvious implementation — {@code Platform.runLater(() -> label.setText(...))} per event — is a
 * textbook way to freeze a JavaFX application. Each {@code runLater} allocates, takes a lock on the
 * platform's pending-runnable queue, and schedules work on the very thread that also has to render;
 * 8 000 of them arriving in a few seconds outruns the FX thread's ability to drain the queue. The
 * window stops repainting, the Cancel button stops responding, and the application looks hung
 * <em>precisely while it is working hardest</em>. The queue also grows without bound, so a long batch
 * can exhaust the heap with pending lambdas that describe a label state nobody will ever see.
 *
 * <h2>Why coalescing is lossless here</h2>
 * The saving grace is the design of {@link ProgressEvent}: {@code completed} and {@code total} are
 * <em>absolute counts, not deltas</em>. Event 900 says "900 of 4000 done" — it does not say "one more
 * than last time". So dropping events 850 through 899 costs nothing at all: the progress bar reaches
 * the same place, one frame later. Had the events been deltas, coalescing would have required summing
 * them, and a bug there would show up as a progress bar that never quite reaches the end.
 *
 * <p>That is <strong>not</strong> true of failures. {@link ProgressEvent.Phase#JOB_FAILED} and
 * {@link ProgressEvent.Phase#JOB_CANCELLED} carry a {@code detail} string that appears nowhere else,
 * and a user who is told "12 failed" needs the twelve names. They therefore go into a bounded queue
 * and every one is delivered, while the high-frequency {@code JOB_STARTED}/{@code JOB_COMPLETED}
 * traffic collapses into a single reference. Two channels, because the two kinds of event have
 * genuinely different requirements — not because one queue was too slow.
 *
 * <h2>Why {@link AnimationTimer} rather than a {@code Timeline} or a scheduled executor</h2>
 * {@code AnimationTimer#handle} is called by the FX toolkit once per pulse, on the FX thread, and only
 * when the toolkit is actually able to render a frame. That makes back-pressure automatic: if the
 * scene graph is busy, pulses come less often and the drain rate falls with it. A
 * {@code ScheduledExecutorService} firing {@code runLater} every 33&nbsp;ms would keep queueing work
 * regardless of whether the FX thread was keeping up, which is the original problem in a smaller
 * costume.
 *
 * <p>The {@link #MIN_INTERVAL_NANOS} gate then limits delivery to ~30&nbsp;Hz. Pulses arrive at
 * display rate — 60&nbsp;Hz, or 144 on a gaming monitor — and there is no purpose in relayouting a
 * label 144 times a second when the human reading it cannot distinguish 30 from 144.
 *
 * <h2>Thread safety</h2>
 * {@link #onProgress} is safe to call from any thread and never blocks, never allocates on the common
 * path, and never touches a JavaFX object. {@link #start()} and {@link #stop()} must be called on the
 * FX thread — {@link AnimationTimer} requires it.
 */
public final class ProgressBridge implements ProgressListener {

    private static final Logger LOG = System.getLogger(ProgressBridge.class.getName());

    /** ~30 Hz. See the class javadoc for why not 60. */
    private static final long MIN_INTERVAL_NANOS = 33_000_000L;

    /**
     * Cap on undelivered failure notices.
     *
     * <p>Bounded because "every failure is important" and "an unbounded queue is safe" cannot both be
     * true: a batch where every image fails — a wrong output format, a full disk — would otherwise
     * queue one object per image and turn a failed batch into an {@link OutOfMemoryError}. 512 is far
     * more than a person will read, and {@link #droppedFailures()} keeps the count honest so the UI
     * can say "and 3 488 more" instead of quietly showing a truncated list as if it were complete.
     */
    private static final int MAX_PENDING_FAILURES = 512;

    /** The most recent high-frequency event; older ones are deliberately overwritten. */
    private final AtomicReference<ProgressEvent> latest = new AtomicReference<>();

    /** Failures and cancellations, every one kept up to {@link #MAX_PENDING_FAILURES}. */
    private final Queue<ProgressEvent> notable = new ConcurrentLinkedQueue<>();

    private final AtomicInteger notableSize = new AtomicInteger();
    private final AtomicLong droppedFailures = new AtomicLong();
    private final AtomicLong coalesced = new AtomicLong();
    private final AtomicLong delivered = new AtomicLong();

    private final Consumer<ProgressEvent> sink;
    private final AnimationTimer timer;
    private long lastDeliveryNanos;

    /**
     * Creates a bridge that delivers coalesced events to {@code sink}; the timer starts with
     * {@link #start()}.
     *
     * @param sink called on the JavaFX application thread with each surviving event. Must not throw —
     *     an exception here would kill the {@link AnimationTimer} and freeze all further progress
     *     updates for the lifetime of the window, so {@link #drain} guards it anyway.
     */
    public ProgressBridge(Consumer<ProgressEvent> sink) {
        this.sink = java.util.Objects.requireNonNull(sink, "sink");
        this.timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (now - lastDeliveryNanos < MIN_INTERVAL_NANOS) {
                    return;
                }
                lastDeliveryNanos = now;
                drain();
            }
        };
    }

    // ------------------------------------------------------------------------
    //  Worker side
    // ------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Called from fork/join workers. Everything here is a lock-free write; the contract on
     * {@link ProgressListener} is that this method is fast and never throws, and a progress update is
     * never worth stalling a worker that could be processing pixels.
     */
    @Override
    public void onProgress(ProgressEvent event) {
        if (event == null) {
            return;
        }
        try {
            switch (event.phase()) {
                // Batch boundaries are rare and structurally important: they flip the view out of
                // LOADING and into LOADED. Treated as notable so they can never be coalesced away by a
                // JOB_COMPLETED that lands in the same 33 ms window.
                case BATCH_STARTED, BATCH_FINISHED, JOB_FAILED, JOB_CANCELLED -> offerNotable(event);
                case JOB_STARTED, JOB_COMPLETED -> {
                    if (latest.getAndSet(event) != null) {
                        coalesced.incrementAndGet();
                    }
                }
            }
        } catch (RuntimeException e) {
            // Belt and braces: the ProgressListener contract forbids throwing into the engine, and a
            // cosmetic update must never abort a batch.
            LOG.log(Level.DEBUG, () -> "progress bridge swallowed " + e);
        }
    }

    private void offerNotable(ProgressEvent event) {
        // Increment first, then decide: a size() call on ConcurrentLinkedQueue is O(n), which on a
        // 512-element queue called once per failure is a needless quadratic.
        if (notableSize.incrementAndGet() > MAX_PENDING_FAILURES) {
            notableSize.decrementAndGet();
            droppedFailures.incrementAndGet();
            return;
        }
        notable.add(event);
    }

    // ------------------------------------------------------------------------
    //  FX side
    // ------------------------------------------------------------------------

    /** Begins delivering events. Must be called on the JavaFX application thread. */
    public void start() {
        lastDeliveryNanos = 0L;
        timer.start();
    }

    /**
     * Stops delivering and flushes whatever is left.
     *
     * <p>The final flush is the difference between a progress bar that ends at 100% and one that
     * freezes at 97% because the last three events arrived inside the final 33&nbsp;ms window. Must be
     * called on the JavaFX application thread.
     */
    public void stop() {
        timer.stop();
        drain();
    }

    /**
     * Delivers pending events: every notable one, then the single surviving frequent one.
     *
     * <p>Order matters. {@code BATCH_FINISHED} must not be delivered before the last
     * {@code JOB_COMPLETED} would have moved the bar to full, and it must not be delivered after a
     * stale {@code JOB_COMPLETED} resets a "finished" label back to "processing". Draining notable
     * events first and the coalesced one last gives the view a final absolute count — which is always
     * the freshest number available — as the last word.
     */
    private void drain() {
        List<ProgressEvent> batch = new ArrayList<>();
        ProgressEvent event;
        while ((event = notable.poll()) != null) {
            notableSize.decrementAndGet();
            batch.add(event);
        }
        ProgressEvent frequent = latest.getAndSet(null);
        if (frequent != null) {
            batch.add(frequent);
        }
        for (ProgressEvent each : batch) {
            try {
                sink.accept(each);
                delivered.incrementAndGet();
            } catch (RuntimeException e) {
                // One bad update must not stop the timer; if it did, every later progress event for the
                // lifetime of the window would be silently discarded.
                LOG.log(Level.WARNING, () -> "progress sink threw on " + each.phase(), e);
            }
        }
    }

    /**
     * Runs {@code action} on the FX thread, whichever thread is calling.
     *
     * <p>{@link Platform#runLater} is correct but not free, and calling it from the FX thread defers
     * work by a frame for no reason — which is visible when the deferred work is "show the error the
     * user just caused". The {@link Platform#isFxApplicationThread()} check is the whole trick.
     */
    public static void onFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    // ------------------------------------------------------------------------
    //  Diagnostics
    // ------------------------------------------------------------------------

    /** Events dropped by coalescing. Expected to be large; it is the mechanism working. */
    public long coalescedEvents() {
        return coalesced.get();
    }

    /** Failure notices dropped because the queue was full. Non-zero means the UI list is truncated. */
    public long droppedFailures() {
        return droppedFailures.get();
    }

    /** Events actually handed to the sink. */
    public long deliveredEvents() {
        return delivered.get();
    }

    @Override
    public String toString() {
        return "ProgressBridge[delivered=" + delivered.get()
                + ", coalesced=" + coalesced.get()
                + ", droppedFailures=" + droppedFailures.get() + "]";
    }
}
