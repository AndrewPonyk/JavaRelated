package com.example.pipeline.application.port;

import com.example.pipeline.domain.PipelineMessage;
import java.time.Duration;

/**
 * A bounded, blocking handoff between two stages.
 *
 * <p>Deliberately a port rather than a raw {@code BlockingQueue}: it lets the
 * application layer stay free of infrastructure types, and it puts the two things
 * stages actually need — <em>bounded</em> {@link #put} for backpressure and typed
 * {@link #putPoisonPills} for shutdown — behind one interface. The default
 * implementation wraps {@code ArrayBlockingQueue}.
 *
 * <p><strong>Contract:</strong> implementations must be safe for multiple
 * concurrent producers and consumers, and must preserve FIFO order so a poison
 * pill can never overtake the data enqueued before it.
 */
public interface MessageChannel {

    /**
     * Enqueues a message, blocking while the channel is full.
     *
     * <p>Blocking is the feature: it is how backpressure from a slow downstream
     * stage reaches the event source. Never replace this with a non-blocking
     * {@code offer} that drops on failure unless dropping is an explicit,
     * counted policy.
     *
     * @throws InterruptedException if interrupted while waiting for space
     */
    void put(PipelineMessage message) throws InterruptedException;

    /**
     * Dequeues the next message, waiting up to {@code timeout}.
     *
     * <p>Stage loops use a timeout rather than an untimed {@code take()} so they
     * can still notice a cooperative stop request when no pill arrives — for
     * example because an upstream stage died.
     *
     * @return the next message, or {@code null} if the timeout elapsed first
     * @throws InterruptedException if interrupted while waiting
     */
    PipelineMessage poll(Duration timeout) throws InterruptedException;

    /**
     * Enqueues exactly {@code count} poison pills, blocking as {@link #put} does.
     *
     * @param count one pill per consumer that must be woken, {@code >= 0}
     * @throws InterruptedException if interrupted while waiting for space
     */
    void putPoisonPills(int count) throws InterruptedException;

    /** Current number of queued messages — a gauge, inherently stale on return. */
    int depth();

    /** Maximum number of queued messages; the hard memory bound of this hop. */
    int capacity();

    /** Stable name used in metrics and diagnostics, e.g. {@code stage1->stage2}. */
    String name();
}
