package com.example.pipeline.infrastructure.generator;

import com.example.pipeline.application.port.EventGenerator;
import com.example.pipeline.domain.SensorEvent;
import com.example.pipeline.domain.SensorType;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;

/**
 * Deterministic, thread-safe synthetic event source.
 *
 * <p><strong>No shared {@code Random}.</strong> Every value is derived from a hash of
 * {@code (seed, sequence)}, so the generator holds no mutable state at all. Two
 * consequences, both of which matter here:
 * <ul>
 *   <li><em>thread-safe by construction</em> — a shared {@code Random} would be a
 *       contended CAS on the hot path, and a {@code ThreadLocalRandom} would make the
 *       stream unreproducible;</li>
 *   <li><em>reproducible</em> — event {@code n} is the same value on every run with
 *       the same seed, regardless of thread interleaving. When a soak run fails at
 *       event 4,812,993, the same seed replays exactly that event stream.</li>
 * </ul>
 *
 * <p>The mixing function is SplitMix64's finaliser: cheap (a handful of ALU ops, no
 * allocation, no memory barrier) and well-distributed enough that sensor ids and types
 * come out evenly spread. This is a load generator, not a cryptographic RNG — see
 * {@code docs/ARCHITECTURE.md} §2.5 for why that is the right trade here.
 *
 * <p>The {@link Clock} is injected so tests can assert on timestamps instead of
 * tolerating {@code Instant.now()}.
 */
public final class SyntheticSensorEventGenerator implements EventGenerator {

    /** SplitMix64 increment (golden-ratio derived), applied per sequence number. */
    private static final long GAMMA = 0x9E3779B97F4A7C15L;

    private static final long MIX_1 = 0xBF58476D1CE4E5B9L;
    private static final long MIX_2 = 0x94D049BB133111EBL;

    private final long seed;
    private final int sensorCount;
    private final Clock clock;
    private final SensorType[] types = SensorType.values();

    /**
     * Generator using the system UTC clock.
     *
     * @param seed        stream seed; the same seed replays the same events
     * @param sensorCount number of distinct sensor ids, {@code >= 1}
     */
    public SyntheticSensorEventGenerator(long seed, int sensorCount) {
        this(seed, sensorCount, Clock.systemUTC());
    }

    /**
     * @param seed        stream seed
     * @param sensorCount number of distinct sensor ids, {@code >= 1}
     * @param clock       timestamp source; inject a fixed clock in tests
     */
    public SyntheticSensorEventGenerator(long seed, int sensorCount, Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (sensorCount < 1) {
            throw new IllegalArgumentException("sensorCount must be >= 1 but was " + sensorCount);
        }
        this.seed = seed;
        this.sensorCount = sensorCount;
    }

    @Override
    public SensorEvent generate(long sequence) {
        if (sequence < 0L) {
            throw new IllegalArgumentException("sequence must be >= 0 but was " + sequence);
        }
        // Three independent draws from one mixed word: high bits pick the sensor, the
        // next bits pick the type, and a second mix supplies the value. Reusing the same
        // word for all three would correlate sensor id with reading.
        long mixed = mix(seed + (sequence + 1L) * GAMMA);
        int sensorIndex = (int) Long.remainderUnsigned(mixed >>> 32, sensorCount);
        SensorType type = types[(int) Long.remainderUnsigned(mixed >>> 16 & 0xFFFFL, types.length)];
        double unitSample = toUnitInterval(mix(mixed));
        double value = type.minValue() + unitSample * type.span();
        return new SensorEvent(sequence, sensorId(sensorIndex), type, value, clock.instant());
    }

    /** Sensor id for an index, e.g. {@code sensor-07}; zero-padded so ids sort naturally. */
    public String sensorId(int index) {
        return String.format(Locale.ROOT, "sensor-%02d", index);
    }

    /** How many distinct sensor ids this generator emits. */
    public int sensorCount() {
        return sensorCount;
    }

    /** The seed — echoed into the run report so a failing run can be replayed. */
    public long seed() {
        return seed;
    }

    /** SplitMix64 finaliser: avalanches the input so adjacent sequences look unrelated. */
    private static long mix(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * MIX_1;
        z = (z ^ (z >>> 27)) * MIX_2;
        return z ^ (z >>> 31);
    }

    /**
     * Maps a 64-bit word onto {@code [0.0, 1.0)}.
     *
     * <p>Uses the top 53 bits — the exact mantissa width of a {@code double} — so every
     * representable value in the range is reachable and none is favoured.
     */
    private static double toUnitInterval(long bits) {
        return (bits >>> 11) * 0x1.0p-53;
    }
}
