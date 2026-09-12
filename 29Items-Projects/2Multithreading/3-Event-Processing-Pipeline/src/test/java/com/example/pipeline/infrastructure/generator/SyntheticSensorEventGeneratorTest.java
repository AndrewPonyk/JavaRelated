package com.example.pipeline.infrastructure.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.pipeline.domain.SensorEvent;
import com.example.pipeline.domain.SensorType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link SyntheticSensorEventGenerator}.
 *
 * <p>The generator holds no mutable state — every event is a pure function of
 * {@code (seed, sequence)} — and these tests are written to pin that down, because it is
 * the property the soak script depends on. When iteration 137 of a soak fails at event
 * 4.8 million, the only way to look at that event again is to replay the seed, and that
 * only works if the stream is genuinely reproducible <em>and</em> independent of which
 * thread asked for it.
 *
 * <p>Statelessness is also why the concurrency test below can assert equality with the
 * single-threaded stream rather than merely checking that nothing threw.
 */
class SyntheticSensorEventGeneratorTest {

    private static final Instant FIXED = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED, ZoneOffset.UTC);

    @Nested
    @DisplayName("determinism")
    class Determinism {

        @Test
        @DisplayName("the same seed replays exactly the same stream")
        void sameSeedSameStream() {
            List<SensorEvent> first = generate(new SyntheticSensorEventGenerator(42L, 8, FIXED_CLOCK), 500);
            List<SensorEvent> second = generate(new SyntheticSensorEventGenerator(42L, 8, FIXED_CLOCK), 500);

            // Whole-list equality, which records compare field by field. Comparing a few
            // spot values would pass even if the type or sensor assignment drifted.
            assertEquals(first, second);
        }

        @Test
        @DisplayName("a different seed gives a different stream")
        void differentSeedDifferentStream() {
            List<SensorEvent> first = generate(new SyntheticSensorEventGenerator(42L, 8, FIXED_CLOCK), 100);
            List<SensorEvent> second = generate(new SyntheticSensorEventGenerator(43L, 8, FIXED_CLOCK), 100);

            assertNotEquals(first, second);
        }

        /**
         * Random access, not just sequential replay. The generator is documented as a
         * function of the sequence number, so asking for event 9,999 directly must give
         * the same answer as walking there — otherwise a "replay from event N" tool
         * could never be written.
         */
        @Test
        @DisplayName("event n is the same whether reached directly or in sequence")
        void isRandomAccess() {
            SyntheticSensorEventGenerator generator = new SyntheticSensorEventGenerator(7L, 4, FIXED_CLOCK);

            SensorEvent walked = null;
            for (long i = 0; i <= 9_999L; i++) {
                walked = generator.generate(i);
            }

            assertEquals(walked, generator.generate(9_999L));
        }

        /**
         * Adjacent sequence numbers must not produce adjacent-looking events. This is
         * what the SplitMix64 finaliser buys: a generator that simply multiplied the
         * sequence would hand every sensor a monotonically rising reading, and a
         * threshold filter over that is not a test of anything.
         */
        @Test
        @DisplayName("adjacent sequences are uncorrelated enough to spread over sensors and types")
        void adjacentSequencesAreMixed() {
            SyntheticSensorEventGenerator generator = new SyntheticSensorEventGenerator(1L, 16, FIXED_CLOCK);

            Set<String> sensors = new HashSet<>();
            Set<SensorType> types = EnumSet.noneOf(SensorType.class);
            for (long i = 0; i < 1_000L; i++) {
                SensorEvent event = generator.generate(i);
                sensors.add(event.sensorId());
                types.add(event.type());
            }

            // 1000 draws over 16 sensors and 4 types. Hitting every bucket is
            // overwhelmingly likely for a well-mixed stream and impossible for a
            // degenerate one; the seed is fixed, so this cannot become flaky.
            assertEquals(16, sensors.size(), sensors.toString());
            assertEquals(SensorType.values().length, types.size(), types.toString());
        }
    }

    @Nested
    @DisplayName("event shape")
    class EventShape {

        /**
         * Every reading must land inside its own type's plausible range. If it did not,
         * {@code SensorEvent}'s constructor would still accept it — the record only
         * rejects non-finite values — and the run would report a temperature of 6,000
         * degrees with nothing having failed.
         */
        @Test
        @DisplayName("every value falls within its sensor type's range")
        void valuesAreInRange() {
            SyntheticSensorEventGenerator generator = new SyntheticSensorEventGenerator(99L, 8, FIXED_CLOCK);

            for (long i = 0; i < 5_000L; i++) {
                SensorEvent event = generator.generate(i);
                assertTrue(event.type().isInRange(event.value()),
                        () -> "out of range: " + event.toLogLine());
            }
        }

        @Test
        @DisplayName("the sequence number is carried through unchanged")
        void carriesTheSequence() {
            SyntheticSensorEventGenerator generator = new SyntheticSensorEventGenerator(0L, 2, FIXED_CLOCK);

            // The sequence is what lets a log line be correlated with a position in the
            // stream, so a generator that renumbered would break replay entirely.
            assertEquals(0L, generator.generate(0L).sequence());
            assertEquals(123_456L, generator.generate(123_456L).sequence());
        }

        @Test
        @DisplayName("sensor ids are zero-padded so they sort naturally")
        void sensorIdsAreZeroPadded() {
            SyntheticSensorEventGenerator generator = new SyntheticSensorEventGenerator(0L, 12, FIXED_CLOCK);

            // "sensor-9" would sort after "sensor-10" in the report; the padding is what
            // makes the console output readable without a numeric-aware sort.
            assertEquals("sensor-00", generator.sensorId(0));
            assertEquals("sensor-07", generator.sensorId(7));
            assertEquals("sensor-11", generator.sensorId(11));
            assertEquals(12, generator.sensorCount());
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2, 3, 16, 64})
        @DisplayName("no event ever names a sensor outside 0..sensorCount-1")
        void staysWithinTheSensorRange(int sensorCount) {
            SyntheticSensorEventGenerator generator =
                    new SyntheticSensorEventGenerator(5L, sensorCount, FIXED_CLOCK);

            Set<String> allowed = new HashSet<>();
            for (int i = 0; i < sensorCount; i++) {
                allowed.add(generator.sensorId(i));
            }

            for (long i = 0; i < 2_000L; i++) {
                String sensorId = generator.generate(i).sensorId();
                assertTrue(allowed.contains(sensorId), sensorId + " is not one of " + allowed);
            }
        }

        /**
         * The clock is injected for exactly this reason: with {@code Instant.now()} there
         * would be no way to assert on the timestamp at all, only to assert that it is
         * "recent", which is another wall-clock assertion waiting to flake.
         */
        @Test
        @DisplayName("the injected clock supplies the timestamp")
        void usesTheInjectedClock() {
            SyntheticSensorEventGenerator generator = new SyntheticSensorEventGenerator(0L, 1, FIXED_CLOCK);
            assertEquals(FIXED, generator.generate(0L).timestamp());
        }

        @Test
        @DisplayName("the seed is readable, so a failing run can report how to replay itself")
        void exposesItsSeed() {
            assertEquals(20260101L, new SyntheticSensorEventGenerator(20260101L, 4, FIXED_CLOCK).seed());
        }
    }

    @Nested
    @DisplayName("guards")
    class Guards {

        @Test
        @DisplayName("rejects a sensor count below 1, a null clock and a negative sequence")
        void rejectsBadArguments() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SyntheticSensorEventGenerator(0L, 0, FIXED_CLOCK));
            assertThrows(NullPointerException.class,
                    () -> new SyntheticSensorEventGenerator(0L, 1, null));

            SyntheticSensorEventGenerator generator = new SyntheticSensorEventGenerator(0L, 1, FIXED_CLOCK);
            assertThrows(IllegalArgumentException.class, () -> generator.generate(-1L));
        }
    }

    @Nested
    @DisplayName("thread safety")
    class ThreadSafety {

        /**
         * Four threads each generating the same range, compared against the
         * single-threaded stream. Because the generator is stateless this is an equality
         * assertion rather than a smoke test — a shared {@code Random} would fail it
         * immediately, and a {@code ThreadLocalRandom} would fail it every run.
         */
        @Test
        @Timeout(60)
        @DisplayName("concurrent generation produces the single-threaded stream")
        void isThreadSafe() throws Exception {
            SyntheticSensorEventGenerator generator = new SyntheticSensorEventGenerator(31L, 8, FIXED_CLOCK);
            List<SensorEvent> expected = generate(generator, 2_000);

            ExecutorService workers = Executors.newFixedThreadPool(4);
            try {
                List<Callable<List<SensorEvent>>> tasks = new ArrayList<>();
                for (int t = 0; t < 4; t++) {
                    tasks.add(() -> generate(generator, 2_000));
                }
                List<Future<List<SensorEvent>>> futures = workers.invokeAll(tasks, 45, TimeUnit.SECONDS);
                for (Future<List<SensorEvent>> future : futures) {
                    assertEquals(expected, future.get());
                }
            } finally {
                workers.shutdownNow();
            }
        }
    }

    private static List<SensorEvent> generate(SyntheticSensorEventGenerator generator, int count) {
        List<SensorEvent> events = new ArrayList<>(count);
        for (long i = 0; i < count; i++) {
            events.add(generator.generate(i));
        }
        return events;
    }
}
