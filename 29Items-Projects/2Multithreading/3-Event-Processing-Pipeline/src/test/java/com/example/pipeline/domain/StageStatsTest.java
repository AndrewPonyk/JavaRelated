package com.example.pipeline.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Per-stage tallies, and the one piece of arithmetic in them that is easy to get wrong.
 *
 * <p>{@link #plusTakesTheLongestElapsedNotTheSum()} is the test worth having. Four filter
 * consumers each running for 500 ms are folded into one {@code StageStats}, and summing
 * their durations would report a two-second stage inside a run that took half a second —
 * a number that not only never happened but makes {@code throughputPerSecond()} understate
 * the pipeline by exactly the consumer count, which is the shape of a fake bottleneck.
 *
 * <p>No test here measures real time. Durations are constructed, so the throughput figures
 * are exact and the assertions can be equalities rather than ranges.
 */
@Timeout(10)
@DisplayName("StageStats")
class StageStatsTest {

    private static StageStats stats(long batches, long in, long out, long rejected, long errors,
            long elapsedMillis) {
        return new StageStats("filter", batches, in, out, rejected, errors,
                Duration.ofMillis(elapsedMillis));
    }

    @Test
    @DisplayName("null components are rejected")
    void nullsAreRejected() {
        assertThrows(NullPointerException.class,
                () -> new StageStats(null, 0L, 0L, 0L, 0L, 0L, Duration.ZERO));
        assertThrows(NullPointerException.class,
                () -> new StageStats("filter", 0L, 0L, 0L, 0L, 0L, null));
    }

    @ParameterizedTest
    @DisplayName("a negative tally is a counting bug and is rejected, not stored")
    @CsvSource({"-1, 0, 0, 0, 0", "0, -1, 0, 0, 0", "0, 0, -1, 0, 0", "0, 0, 0, -1, 0", "0, 0, 0, 0, -1"})
    void negativeTalliesAreRejected(long batches, long in, long out, long rejected, long errors) {
        // A negative count can only come from a decrement that should not exist; storing it
        // would surface later as a nonsensical report rather than at the point of the bug.
        assertThrows(IllegalArgumentException.class,
                () -> new StageStats("filter", batches, in, out, rejected, errors, Duration.ZERO));
    }

    @Test
    @DisplayName("a negative elapsed time is rejected")
    void negativeElapsedIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new StageStats("filter", 0L, 0L, 0L, 0L, 0L, Duration.ofMillis(-1L)));
    }

    @Test
    @DisplayName("zero is a legal tally - a stage that handled nothing is not an error")
    void zeroesAreAccepted() {
        StageStats empty = StageStats.empty("producer");
        assertEquals("producer", empty.stage());
        assertEquals(0L, empty.batches());
        assertEquals(0L, empty.eventsIn());
        assertEquals(0L, empty.eventsOut());
        assertEquals(0L, empty.eventsRejected());
        assertEquals(0L, empty.errors());
        assertEquals(Duration.ZERO, empty.elapsed());
    }

    @Test
    @DisplayName("plus sums every tally")
    void plusSumsTallies() {
        StageStats sum = stats(2L, 20L, 12L, 8L, 1L, 300L).plus(stats(3L, 30L, 18L, 12L, 2L, 100L));
        assertEquals(5L, sum.batches());
        assertEquals(50L, sum.eventsIn());
        assertEquals(30L, sum.eventsOut());
        assertEquals(20L, sum.eventsRejected());
        assertEquals(3L, sum.errors());
    }

    /**
     * The reason {@code plus} is not a plain field-by-field sum. Consumers run concurrently,
     * so the stage's wall-clock time is the longest consumer's, never the total.
     */
    @Test
    @DisplayName("plus takes the longest elapsed time, not the sum")
    void plusTakesTheLongestElapsedNotTheSum() {
        StageStats first = stats(1L, 10L, 10L, 0L, 0L, 500L);
        StageStats second = stats(1L, 10L, 10L, 0L, 0L, 300L);
        assertEquals(Duration.ofMillis(500L), first.plus(second).elapsed());
        // Commutative in the elapsed field too, or folding a consumer list would depend on
        // the order the futures happened to complete in.
        assertEquals(Duration.ofMillis(500L), second.plus(first).elapsed());
    }

    @Test
    @DisplayName("plus keeps the receiver's stage name")
    void plusKeepsTheStageName() {
        StageStats folded = StageStats.empty("filter").plus(StageStats.empty("filter-consumer-2"));
        assertEquals("filter", folded.stage(),
                "the fold produces one stage's stats, so the receiver names it");
    }

    @Test
    @DisplayName("plus rejects a null operand")
    void plusRejectsNull() {
        assertThrows(NullPointerException.class, () -> StageStats.empty("filter").plus(null));
    }

    @Test
    @DisplayName("empty() is the identity for plus")
    void emptyIsTheIdentity() {
        StageStats stats = stats(2L, 20L, 12L, 8L, 1L, 300L);
        assertEquals(stats, stats.plus(StageStats.empty("filter")));
    }

    @Test
    @DisplayName("folding four concurrent consumers reports one stage's worth of time")
    void foldingFourConsumers() {
        StageStats folded = stats(1L, 25L, 25L, 0L, 0L, 480L)
                .plus(stats(1L, 25L, 25L, 0L, 0L, 500L))
                .plus(stats(1L, 25L, 25L, 0L, 0L, 470L))
                .plus(stats(1L, 25L, 25L, 0L, 0L, 495L));
        assertEquals(100L, folded.eventsIn());
        assertEquals(Duration.ofMillis(500L), folded.elapsed());
        // 100 events in 500 ms is 200/s; summing the durations would have reported 51/s.
        assertEquals(200.0, folded.throughputPerSecond(), 0.001);
    }

    @Test
    @DisplayName("throughput is events per second over eventsIn")
    void throughputIsExact() {
        assertEquals(2000.0, stats(1L, 1000L, 1000L, 0L, 0L, 500L).throughputPerSecond(), 0.001);
    }

    @Test
    @DisplayName("a zero elapsed time yields zero throughput, never a division by zero")
    void zeroElapsedDoesNotDivideByZero() {
        // A stage that finished inside the clock's resolution must not report Infinity, which
        // would propagate into the log line as a meaningless rate.
        double rate = new StageStats("filter", 1L, 100L, 100L, 0L, 0L, Duration.ZERO)
                .throughputPerSecond();
        assertEquals(0.0, rate);
        assertTrue(Double.isFinite(rate), "Infinity in a report is worse than zero");
    }

    @Test
    @DisplayName("the log line names every field, so it is greppable without a log pipeline")
    void logLineIsComplete() {
        String line = stats(3L, 300L, 200L, 100L, 2L, 1500L).toLogLine();
        assertEquals("stage=filter batches=3 in=300 out=200 rejected=100 errors=2 "
                + "elapsed=1500ms rate=200/s", line);
    }

    @Test
    @DisplayName("the log line is one line and ASCII only")
    void logLineIsOneAsciiLine() {
        String line = stats(1L, 1L, 1L, 0L, 0L, 1L).toLogLine();
        assertEquals(1, line.lines().count(), line);
        for (int i = 0; i < line.length(); i++) {
            assertTrue(line.charAt(i) < 0x80, line);
        }
    }

    @Test
    @DisplayName("the rate is rendered under Locale.ROOT, so a comma locale cannot change the format")
    void logLineUsesRootLocale() {
        // A German default locale would otherwise print rate=1.234/s and break any parser.
        assertTrue(stats(1L, 1234L, 0L, 0L, 0L, 1000L).toLogLine().endsWith("rate=1234/s"),
                stats(1L, 1234L, 0L, 0L, 0L, 1000L).toLogLine());
    }
}
