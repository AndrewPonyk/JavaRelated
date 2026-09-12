package com.parallelimage.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ImageProcessingEngineTest#statsDescribeThePool()} only pins loose properties of a
 * live-sampled snapshot ("parallelism equals 4", "stealCount &gt;= 0", "utilisation between 0 and
 * 1"). These tests instead construct {@link EngineStats} directly with hand-picked counters, so the
 * exact arithmetic in {@code utilisation()} and {@code summary()} — and the {@code pool == null}
 * guard in {@code sample()} — can be pinned to precomputed expected values.
 */
class EngineStatsTest {

    private static EngineStats stats(int parallelism, int activeThreads) {
        return new EngineStats(
                parallelism, activeThreads, 0, 0L, 0, 0L, 0, 0.0d, false, 0.0d, 0L);
    }

    @Test
    @DisplayName("utilisation() is 0.0 when parallelism is zero, regardless of active threads")
    void utilisationIsZeroWhenParallelismIsZero() {
        assertEquals(0.0d, stats(0, 3).utilisation());
    }

    @Test
    @DisplayName("utilisation() is 0.0 when parallelism is negative")
    void utilisationIsZeroWhenParallelismIsNegative() {
        assertEquals(0.0d, stats(-2, 3).utilisation());
    }

    @Test
    @DisplayName("utilisation() computes the exact activeThreads/parallelism ratio below capacity")
    void utilisationComputesExactRatioBelowCapacity() {
        assertEquals(0.25d, stats(8, 2).utilisation(), 1e-12d);
    }

    @Test
    @DisplayName("utilisation() is exactly 1.0 when active threads equal parallelism")
    void utilisationIsOneAtExactCapacity() {
        assertEquals(1.0d, stats(5, 5).utilisation(), 1e-12d);
    }

    @Test
    @DisplayName("utilisation() clamps to 1.0 when active threads exceed parallelism")
    void utilisationClampsAboveCapacity() {
        assertEquals(1.0d, stats(4, 6).utilisation(), 1e-12d);
    }

    @Test
    @DisplayName("summary() formats every field exactly, with the default-GC label")
    void summaryFormatsExactLineWithDefaultGc() {
        EngineStats stats = new EngineStats(
                8, 3, 3, 12L, 0, 45L, 20, 0.42d, false, 0.73d, 1500L);

        assertEquals(
                "3/8 workers · 12 queued · 45 steals · metadata 20 (42% optimistic) "
                        + "· heap 73% · gc 1500ms · default GC",
                stats.summary());
    }

    @Test
    @DisplayName("summary() switches to the Shenandoah label and formats the 0%/100% boundaries")
    void summaryFormatsExactLineWithShenandoah() {
        EngineStats stats = new EngineStats(
                1, 1, 1, 0L, 0, 0L, 0, 1.0d, true, 0.0d, 0L);

        assertEquals(
                "1/1 workers · 0 queued · 0 steals · metadata 0 (100% optimistic) "
                        + "· heap 0% · gc 0ms · Shenandoah",
                stats.summary());
    }

    @Test
    @DisplayName("sample() returns the shared UNAVAILABLE instance when the pool is null")
    void sampleReturnsUnavailableWhenPoolIsNull() {
        assertSame(EngineStats.UNAVAILABLE, EngineStats.sample(null, null));
    }
}
