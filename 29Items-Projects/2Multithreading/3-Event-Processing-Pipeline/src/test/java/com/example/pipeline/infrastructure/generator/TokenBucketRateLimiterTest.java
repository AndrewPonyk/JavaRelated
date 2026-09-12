package com.example.pipeline.infrastructure.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.pipeline.application.port.RateLimiter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pacing verified as arithmetic, not as elapsed wall-clock time.
 *
 * <p>The limiter takes a clock and a sleeper through a package-private constructor precisely
 * so this class can exist. {@link FakeClock} is both: {@code getAsLong} reports a time the
 * test controls, and {@code sleepNanos} records the requested duration and <em>advances the
 * clock by it</em> instead of spending it. That second half is what makes the fake faithful —
 * a sleeper that recorded without advancing would let the limiter believe no time had passed
 * and every assertion about steady-state pacing would be measuring a fiction.
 *
 * <p>Every expected value below is derived from the two constants the class documents:
 * {@code nanosPerPermit = max(1, 1e9 / rate)} and a burst allowance capped at one second.
 * Nothing here can fail because a CI runner was loaded.
 */
@Timeout(10)
@DisplayName("TokenBucketRateLimiter")
class TokenBucketRateLimiterTest {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    /** 1000 permits/s, so one permit costs exactly 1 ms — arithmetic a reader can follow. */
    private static final long RATE = 1_000L;
    private static final long NANOS_PER_PERMIT = NANOS_PER_SECOND / RATE;

    /**
     * A clock the test advances by hand, doubling as the limiter's sleeper.
     *
     * <p>Deliberately not thread-safe: the limiter is exercised from the test thread only.
     */
    private static final class FakeClock implements LongSupplier, TokenBucketRateLimiter.NanoSleeper {

        private final List<Long> sleeps = new ArrayList<>();
        private long nanos;

        FakeClock(long startNanos) {
            this.nanos = startNanos;
        }

        @Override
        public long getAsLong() {
            return nanos;
        }

        @Override
        public void sleepNanos(long requested) {
            sleeps.add(requested);
            nanos += requested; // Sleeping spends time; a fake that forgot this would lie.
        }

        /** Simulates the caller doing something else for {@code delta} nanoseconds. */
        void advance(long delta) {
            nanos += delta;
        }

        long sleepCount() {
            return sleeps.size();
        }

        long lastSleep() {
            return sleeps.get(sleeps.size() - 1);
        }

        long totalSlept() {
            return sleeps.stream().mapToLong(Long::longValue).sum();
        }
    }

    private static TokenBucketRateLimiter limiter(FakeClock clock) {
        return new TokenBucketRateLimiter(RATE, clock, clock);
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @ParameterizedTest
        @DisplayName("a rate below one permit per second is rejected")
        @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
        void nonPositiveRateIsRejected(long rate) {
            assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(rate));
        }

        @Test
        @DisplayName("a null clock or sleeper is rejected")
        void nullCollaboratorsAreRejected() {
            assertThrows(NullPointerException.class,
                    () -> new TokenBucketRateLimiter(RATE, null, nanos -> { }));
            assertThrows(NullPointerException.class,
                    () -> new TokenBucketRateLimiter(RATE, () -> 0L, null));
        }

        @Test
        @DisplayName("the configured rate is reported back")
        void rateIsReported() {
            assertEquals(RATE, new TokenBucketRateLimiter(RATE).permitsPerSecond());
        }

        @Test
        @DisplayName("of(0) yields an unlimited limiter, not a token bucket")
        void zeroMeansUnlimited() throws Exception {
            RateLimiter unlimited = TokenBucketRateLimiter.of(0L);
            assertFalse(unlimited instanceof TokenBucketRateLimiter,
                    "0 must map onto the no-pacing limiter, not a bucket of 0 permits");
            unlimited.acquire(Integer.MAX_VALUE); // Must not block, and must not throw.
        }

        @Test
        @DisplayName("of() treats a negative rate as unlimited too")
        void negativeMeansUnlimited() {
            assertFalse(TokenBucketRateLimiter.of(-7L) instanceof TokenBucketRateLimiter);
        }

        @Test
        @DisplayName("of() with a positive rate builds a token bucket")
        void positiveRateBuildsABucket() {
            RateLimiter limiter = TokenBucketRateLimiter.of(RATE);
            assertEquals(RATE, assertInstanceOf(TokenBucketRateLimiter.class, limiter).permitsPerSecond());
        }
    }

    @Nested
    @DisplayName("acquire")
    class Acquire {

        @ParameterizedTest
        @DisplayName("a non-positive permit count is rejected and costs nothing")
        @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
        void nonPositivePermitsAreRejected(int permits) {
            FakeClock clock = new FakeClock(0L);
            TokenBucketRateLimiter limiter = limiter(clock);
            assertThrows(IllegalArgumentException.class, () -> limiter.acquire(permits));
            assertEquals(0L, clock.sleepCount(), "a rejected call must not have paced anything");
        }

        @Test
        @DisplayName("the first acquire never waits")
        void firstAcquireIsFree() throws Exception {
            FakeClock clock = new FakeClock(0L);
            limiter(clock).acquire(1);
            assertEquals(0L, clock.sleepCount(), "a fresh bucket owes nothing");
        }

        @Test
        @DisplayName("back-to-back acquires each wait exactly one permit's worth")
        void steadyStatePacesOnePermitAtATime() throws Exception {
            FakeClock clock = new FakeClock(0L);
            TokenBucketRateLimiter limiter = limiter(clock);
            for (int i = 0; i < 5; i++) {
                limiter.acquire(1);
            }
            // Five permits at 1 ms each, the first of them free: four sleeps of exactly 1 ms.
            assertEquals(4L, clock.sleepCount());
            assertEquals(4 * NANOS_PER_PERMIT, clock.totalSlept());
            assertEquals(NANOS_PER_PERMIT, clock.lastSleep());
        }

        @Test
        @DisplayName("a batch costs its permit count, not one permit")
        void batchCostScalesWithPermits() throws Exception {
            FakeClock clock = new FakeClock(0L);
            TokenBucketRateLimiter limiter = limiter(clock);
            limiter.acquire(10); // Free, but leaves 10 ms owing.
            assertEquals(0L, clock.sleepCount());
            limiter.acquire(1);
            assertEquals(10 * NANOS_PER_PERMIT, clock.lastSleep(),
                    "a 10-permit batch must have consumed 10 permits of the budget");
        }

        @Test
        @DisplayName("time spent elsewhere is credited, so the next few acquires are free")
        void idleTimeIsCreditedForward() throws Exception {
            FakeClock clock = new FakeClock(0L);
            TokenBucketRateLimiter limiter = limiter(clock);
            limiter.acquire(1); // Free; the next permit falls due at 1 ms.
            clock.advance(5 * NANOS_PER_PERMIT); // Caller was busy for 5 ms.

            // The next permit was due at 1 ms and it is now 5 ms, so permits due at 1, 2, 3, 4
            // and 5 ms are all payable immediately: five free acquires, then pacing resumes.
            for (int i = 0; i < 5; i++) {
                limiter.acquire(1);
                assertEquals(0L, clock.sleepCount(), "permit " + i + " should have been free");
            }
            limiter.acquire(1);
            assertEquals(1L, clock.sleepCount(), "the credit must run out after five permits");
            assertEquals(NANOS_PER_PERMIT, clock.lastSleep());
        }

        @Test
        @DisplayName("interruption propagates out of acquire")
        void interruptionPropagates() {
            TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(RATE, () -> 0L, nanos -> {
                throw new InterruptedException("interrupted while pacing");
            });
            assertThrows(InterruptedException.class, () -> {
                limiter.acquire(1); // Free; sets up the debt.
                limiter.acquire(1); // Must wait, and the sleeper refuses.
            });
        }
    }

    @Nested
    @DisplayName("burst allowance")
    class BurstAllowance {

        /**
         * The property the {@code maxBurstNanos} cap exists for. Without it, credit for the
         * whole idle period accrues and a limiter that sat unused for ten seconds would hand
         * out ten seconds of events at once — the queue bound would absorb the flood, but the
         * configured rate would be a fiction.
         */
        @Test
        @DisplayName("credit for a long idle period is capped at one second's worth")
        void longIdlePeriodDoesNotAccrueUnboundedCredit() throws Exception {
            FakeClock clock = new FakeClock(0L);
            TokenBucketRateLimiter limiter = limiter(clock);
            clock.advance(10 * NANOS_PER_SECOND); // Ten seconds of idleness.

            // Two seconds' worth of permits: free, because at most one second of credit was
            // carried, and the second second is borrowed from the future.
            limiter.acquire((int) (2 * RATE));
            assertEquals(0L, clock.sleepCount());

            // The borrowing is what proves the cap. With ten seconds of credit this would
            // still be free; with one second of credit the bucket is now 1 s in debt.
            limiter.acquire(1);
            assertEquals(1L, clock.sleepCount());
            assertEquals(NANOS_PER_SECOND, clock.lastSleep(),
                    "credit beyond one second must have been discarded");
        }

        @Test
        @DisplayName("a single acquire cannot outrun the rate over the long run")
        void averageRateConvergesOnTheTarget() throws Exception {
            FakeClock clock = new FakeClock(0L);
            TokenBucketRateLimiter limiter = limiter(clock);
            int batches = 50;
            int permitsPerBatch = 20;
            for (int i = 0; i < batches; i++) {
                limiter.acquire(permitsPerBatch);
            }
            long permits = (long) batches * permitsPerBatch;
            // Every permit but the first batch's was paid for at exactly the target rate.
            assertEquals((permits - permitsPerBatch) * NANOS_PER_PERMIT, clock.totalSlept());
            assertEquals(clock.totalSlept(), clock.getAsLong(), "no time came from anywhere else");
        }
    }

    @Nested
    @DisplayName("clock edge cases")
    class ClockEdgeCases {

        /**
         * {@code System.nanoTime()} is documented as an arbitrary origin that wraps after
         * ~292 years of uptime, and is routinely negative on Linux. The limiter compares
         * {@code now - nextPermitNanos} against zero rather than comparing the two directly;
         * this pins that choice. A naive {@code now < nextPermitNanos} would see a positive
         * {@code now} and a wrapped, negative {@code nextPermitNanos}, conclude no wait was
         * needed, and emit an unbounded burst at exactly the wrong moment.
         */
        @Test
        @DisplayName("pacing survives nanoTime wrapping past Long.MAX_VALUE")
        void wrapAroundIsHandled() throws Exception {
            FakeClock clock = new FakeClock(Long.MAX_VALUE - NANOS_PER_PERMIT / 2);
            TokenBucketRateLimiter limiter = limiter(clock);
            limiter.acquire(1); // Free; pushes the next-permit time over the wrap point.
            assertEquals(0L, clock.sleepCount());

            limiter.acquire(1);
            assertEquals(NANOS_PER_PERMIT, clock.lastSleep(),
                    "the wait must be one permit, not a garbage value from a wrapped comparison");
            assertTrue(clock.getAsLong() < 0L, "the fake clock should have wrapped, exercising the path");
        }

        @Test
        @DisplayName("a rate finer than one permit per nanosecond still paces by one nanosecond")
        void rateFasterThanTheClockFloorsAtOneNanosecond() throws Exception {
            FakeClock clock = new FakeClock(0L);
            // 2e9 permits/s would be 0.5 ns per permit; integer division floors to 0, and a
            // 0-nanosecond cost would make the limiter a no-op. The constructor clamps to 1.
            TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2 * NANOS_PER_SECOND, clock, clock);
            limiter.acquire(1);
            limiter.acquire(1);
            assertEquals(1L, clock.lastSleep(), "the per-permit cost must never round down to zero");
        }

        @Test
        @DisplayName("one permit per second paces a full second per event")
        void slowestRateIsHonoured() throws Exception {
            FakeClock clock = new FakeClock(0L);
            TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1L, clock, clock);
            limiter.acquire(1);
            limiter.acquire(1);
            assertEquals(NANOS_PER_SECOND, clock.lastSleep());
        }
    }
}
