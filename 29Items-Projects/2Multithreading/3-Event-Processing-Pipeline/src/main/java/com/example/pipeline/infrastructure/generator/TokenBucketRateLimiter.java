package com.example.pipeline.infrastructure.generator;

import com.example.pipeline.application.port.RateLimiter;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Token-bucket pacer over {@link System#nanoTime()}.
 *
 * <p><strong>Why a virtual "next permit time" instead of sleeping per batch:</strong> a
 * naive limiter sleeps {@code permits / rate} each round and drifts, because every sleep
 * overshoots by the OS timer granularity and the error accumulates. Tracking an absolute
 * {@code nextPermitNanos} instead means an early batch borrows against the next one, so
 * the <em>long-run average</em> converges on the target even though individual sleeps
 * are imprecise.
 *
 * <p><strong>{@code nanoTime}, never {@code currentTimeMillis}:</strong> the latter is
 * wall-clock and jumps when NTP corrects the system time — a backwards jump would let
 * the producer emit an unbounded burst. {@code nanoTime} is monotonic by contract.
 *
 * <p><strong>The burst allowance is deliberately bounded.</strong> Idle time accrues
 * credit only up to {@link #maxBurstNanos}; without that cap, a limiter idle for a
 * minute would allow a minute's worth of events instantly and blow through the queue
 * bound — backpressure would absorb it, but the "rate limit" would be a fiction.
 *
 * <p>Thread-safe via a single lock. The producer stage is single-threaded, so the lock
 * is uncontended in practice; it is here so the class is not a trap if a second producer
 * is ever added.
 *
 * <p><strong>The clock and the sleep are injectable</strong> (package-private constructor).
 * With {@code System.nanoTime()} baked in, the only way to test pacing was to run the
 * limiter and assert on elapsed wall-clock time — exactly the flaky shape this project
 * forbids, so the class went untested. A test now supplies a clock it advances by hand and
 * a sleeper that records the requested duration instead of spending it, which turns "does
 * it pace correctly" into arithmetic: exact, instant, and immune to a loaded CI runner.
 */
public final class TokenBucketRateLimiter implements RateLimiter {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    /** Credit for at most this many seconds of idleness may be carried forward. */
    private static final long MAX_BURST_SECONDS = 1L;

    private final long permitsPerSecond;
    private final long nanosPerPermit;
    private final long maxBurstNanos;
    private final LongSupplier clock;
    private final NanoSleeper sleeper;
    private final Object lock = new Object();
    private long nextPermitNanos;

    /**
     * How the limiter waits. Separate from the clock because a test needs to <em>advance</em>
     * time rather than spend it; {@link TimeUnit#sleep(long)} is the production implementation.
     */
    interface NanoSleeper {

        /**
         * @param nanos duration to pause for; always {@code > 0} when called
         * @throws InterruptedException if the waiting thread is interrupted
         */
        void sleepNanos(long nanos) throws InterruptedException;
    }

    /**
     * @param permitsPerSecond target rate, {@code >= 1}; use
     *                         {@link RateLimiter#unlimited()} for no pacing
     */
    public TokenBucketRateLimiter(long permitsPerSecond) {
        this(permitsPerSecond, System::nanoTime, TimeUnit.NANOSECONDS::sleep);
    }

    /**
     * Test seam.
     *
     * @param permitsPerSecond target rate, {@code >= 1}
     * @param clock            monotonic nanosecond source
     * @param sleeper          how a computed wait is spent
     */
    TokenBucketRateLimiter(long permitsPerSecond, LongSupplier clock, NanoSleeper sleeper) {
        if (permitsPerSecond < 1L) {
            throw new IllegalArgumentException("permitsPerSecond must be >= 1 but was " + permitsPerSecond);
        }
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        this.permitsPerSecond = permitsPerSecond;
        this.nanosPerPermit = Math.max(1L, NANOS_PER_SECOND / permitsPerSecond);
        this.maxBurstNanos = MAX_BURST_SECONDS * NANOS_PER_SECOND;
        this.nextPermitNanos = clock.getAsLong();
    }

    /**
     * Factory that maps {@code 0} onto an unlimited limiter.
     *
     * <p>Keeps the "0 means unbounded" convention in one place rather than repeating the
     * conditional at every call site.
     *
     * @param permitsPerSecond target rate, or {@code 0} for no pacing
     */
    public static RateLimiter of(long permitsPerSecond) {
        return permitsPerSecond <= 0L ? RateLimiter.unlimited() : new TokenBucketRateLimiter(permitsPerSecond);
    }

    @Override
    public void acquire(int permits) throws InterruptedException {
        if (permits < 1) {
            throw new IllegalArgumentException("permits must be >= 1 but was " + permits);
        }
        long waitNanos;
        synchronized (lock) {
            long now = clock.getAsLong();
            // Comparison is subtraction-based: nanoTime() may be negative and wraps, so
            // `now < nextPermitNanos` is wrong and `now - nextPermitNanos < 0` is right.
            if (now - nextPermitNanos > maxBurstNanos) {
                nextPermitNanos = now - maxBurstNanos;
            }
            long cost = nanosPerPermit * permits;
            waitNanos = nextPermitNanos - now;
            nextPermitNanos += cost;
        }
        if (waitNanos > 0L) {
            // Sleep, not spin: the producer has nothing useful to do, and burning a core
            // to be 1 ms more punctual is a bad trade in a CPU-bound pipeline.
            sleeper.sleepNanos(waitNanos);
        }
    }

    /** The configured target rate. */
    public long permitsPerSecond() {
        return permitsPerSecond;
    }
}
