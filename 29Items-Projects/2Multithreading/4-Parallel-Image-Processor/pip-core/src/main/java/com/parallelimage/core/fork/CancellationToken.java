package com.parallelimage.core.fork;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative cancellation shared by every task in one batch's fork/join tree.
 *
 * <h2>Why this exists</h2>
 * {@link java.util.concurrent.ForkJoinTask#cancel(boolean)} only prevents tasks that have
 * <em>not yet started</em> from running; despite the {@code mayInterruptIfRunning} parameter it does
 * <strong>not</strong> interrupt a task already inside {@code compute()}. A tile kernel grinding
 * through 48 million pixels will therefore run to completion no matter how hard the UI's Cancel
 * button is pressed. See {@code docs/TECH-NOTES.md} §3.6 A6.
 *
 * <p>The fix is cooperative: tasks poll this token at every {@code compute()} entry and, for long
 * leaves, once per scanline band. Polling an uncontended {@link AtomicBoolean} costs a single
 * volatile read (a few nanoseconds) so it is free at those granularities — but it would <em>not</em>
 * be free per pixel, which is why kernels check per row, never per pixel.
 *
 * <p><strong>Thread-safe.</strong> One instance per batch, shared across all workers.
 */
public final class CancellationToken {

    /**
     * A token that is never cancelled, for callers that do not offer cancellation at all.
     *
     * <p>Genuinely un-cancellable, not merely "not expected to be cancelled". It is a process-wide
     * singleton reached from every default constructor in the codebase, so a single stray
     * {@code NONE.cancel()} would abort every kernel in the JVM for the remainder of its life — and
     * two paths reach that call without meaning to: {@code ImageProcessingEngine.close()} cancelling
     * "the active batch" when no batch is running, and {@link #throwIfCancelledOrInterrupted()}
     * cancelling its own token on interruption. Both are correct against a per-batch token and
     * catastrophic against a shared one, so the guarantee belongs here rather than in a rule every
     * future call site has to remember.
     */
    public static final CancellationToken NONE = new CancellationToken(false);

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /** {@code false} only for {@link #NONE}. */
    private final boolean cancellable;

    public CancellationToken() {
        this(true);
    }

    private CancellationToken(boolean cancellable) {
        this.cancellable = cancellable;
    }

    /**
     * Requests cancellation. Idempotent; returns {@code true} if this call flipped the flag.
     *
     * @return {@code false} always for {@link #NONE}
     */
    public boolean cancel() {
        return cancellable && cancelled.compareAndSet(false, true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * Aborts the current task if cancellation was requested.
     *
     * <p>Throws {@link CancellationException} — unchecked, so it propagates cleanly out of
     * {@code compute()}, which cannot declare checked exceptions.
     */
    public void throwIfCancelled() {
        if (cancelled.get()) {
            throw new CancellationException("batch cancelled");
        }
    }

    /**
     * Also honours thread interruption, restoring the interrupt flag before failing.
     *
     * <p>Swallowing an interrupt is one of the classic concurrency bugs: it strips information the
     * caller above needs in order to shut down. Always restore it (ARCHITECTURE §2.6 rule 5).
     */
    public void throwIfCancelledOrInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            cancel();
            throw new CancellationException("worker interrupted");
        }
        throwIfCancelled();
    }
}
