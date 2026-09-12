package com.example.pipeline.application.port;

import java.time.Duration;

/**
 * Control over the thread pools the stages run on, without exposing
 * {@code ExecutorService} to the application layer.
 *
 * <p>The orchestrator needs exactly three capabilities: drain politely, escalate
 * when the drain overruns, and release resources. Anything more (submitting tasks,
 * inspecting queues) belongs to the infrastructure adapter that owns the pools.
 */
public interface StageLifecycle extends AutoCloseable {

    /**
     * Stops accepting new work and waits for running tasks to finish.
     *
     * @return {@code true} if every pool terminated within {@code timeout}
     * @throws InterruptedException if interrupted while waiting
     */
    boolean shutdownAndAwait(Duration timeout) throws InterruptedException;

    /**
     * Interrupts everything still running — the escalation after a drain overruns.
     *
     * <p>Events in flight may be lost; the caller is expected to report that rather
     * than treat the run as successful.
     */
    void forceShutdown();

    /** Releases all pools; idempotent, and never throws a checked exception. */
    @Override
    void close();
}
