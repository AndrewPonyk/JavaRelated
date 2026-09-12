package com.example.pipeline.infrastructure.concurrent;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread factory producing named, non-daemon threads with a logging uncaught-exception
 * handler.
 *
 * <p><strong>Names are a debugging tool, not decoration.</strong> The default
 * {@code pool-2-thread-3} tells you nothing; {@code pipeline-filter-3} tells you which
 * stage is stuck the moment you look at a thread dump or a profiler flame graph. In a
 * concurrency project this is the single cheapest observability win available.
 *
 * <p><strong>Non-daemon by default</strong> so a stage still draining keeps the JVM
 * alive: exiting while the aggregator is mid-batch is exactly the lost-work scenario
 * the poison-pill shutdown exists to prevent. The dashboard thread is the one place
 * that wants {@code daemon = true} — it has no state worth waiting for.
 *
 * <p>The uncaught-exception handler exists because a thread that dies silently in a
 * pipeline does not look like a crash; it looks like a stage that mysteriously stopped
 * consuming, which is far harder to diagnose.
 */
public final class NamedThreadFactory implements ThreadFactory {

    private static final Logger LOG = Logger.getLogger(NamedThreadFactory.class.getName());

    private final String prefix;
    private final boolean daemon;
    private final AtomicInteger counter = new AtomicInteger();

    /**
     * Non-daemon factory.
     *
     * @param prefix name prefix; threads are {@code prefix-0}, {@code prefix-1}, ...
     */
    public NamedThreadFactory(String prefix) {
        this(prefix, false);
    }

    /**
     * @param prefix name prefix
     * @param daemon whether the threads should be daemon threads
     */
    public NamedThreadFactory(String prefix, boolean daemon) {
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        this.daemon = daemon;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        Thread thread = new Thread(runnable, prefix + "-" + counter.getAndIncrement());
        thread.setDaemon(daemon);
        thread.setUncaughtExceptionHandler((t, throwable) ->
                LOG.log(Level.SEVERE, "uncaught exception in thread " + t.getName(), throwable));
        return thread;
    }

    /** How many threads this factory has created — asserted in tests. */
    public int created() {
        return counter.get();
    }
}
