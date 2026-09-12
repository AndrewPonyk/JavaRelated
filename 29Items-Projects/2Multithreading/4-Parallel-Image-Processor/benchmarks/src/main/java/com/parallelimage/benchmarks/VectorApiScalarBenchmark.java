package com.parallelimage.benchmarks;

import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Compares a scalar grayscale-luma inner loop (matching {@code GrayscaleFilter}'s packed-ARGB
 * bit-shift approach) against a {@code jdk.incubator.vector} loop over the same arithmetic.
 *
 * <p>{@code GrayscaleFilter}/{@code BoxBlurFilter} in {@code pip-core} read/write packed ARGB
 * {@code int[]} pixels — one int per pixel, channels extracted via bit shifts. That layout is not
 * itself vectorizable; the multiply-add-shift arithmetic underneath it is. This benchmark isolates
 * the arithmetic ({@link #vectorPreSeparated}) to show the Vector API's ceiling, and includes a
 * deinterleave-included variant ({@link #vectorWithDeinterleave}) to show what adopting it in
 * production would actually cost end to end. See
 * {@code docs/adr/0001-vector-api-for-pixel-kernels.md} for the resulting decision.
 *
 * <p>Needs the incubator module at both compile and run time. Run: {@code java --add-modules
 * jdk.incubator.vector -jar benchmarks/target/benchmarks.jar VectorApiScalarBenchmark -f 1 -wi 2
 * -i 3 -jvmArgs "--add-modules jdk.incubator.vector"}. The {@code -jvmArgs} flag is required too —
 * JMH forks a fresh JVM per benchmark run by default (see {@code @Fork}), and that forked JVM
 * needs the module added independently of the one launching {@code Main}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
public class VectorApiScalarBenchmark {

    private static final VectorSpecies<Integer> SPECIES = IntVector.SPECIES_PREFERRED;

    private static final int W_RED = 13_933;
    private static final int W_GREEN = 46_871;
    private static final int W_BLUE = 4_732;
    private static final int SHIFT = 16;

    /** A small, a mid-size tile, and a large tile-worth of pixels. */
    @Param({"4096", "65536", "1048576"})
    public int pixelCount;

    private int[] packedArgb;
    private int[] red;
    private int[] green;
    private int[] blue;
    private int[] grayOut;

    @Setup(Level.Trial)
    public void setUp() {
        RandomGenerator random = RandomGenerator.of("L64X128MixRandom");
        packedArgb = new int[pixelCount];
        red = new int[pixelCount];
        green = new int[pixelCount];
        blue = new int[pixelCount];
        grayOut = new int[pixelCount];
        for (int i = 0; i < pixelCount; i++) {
            int r = random.nextInt(256);
            int g = random.nextInt(256);
            int b = random.nextInt(256);
            red[i] = r;
            green[i] = g;
            blue[i] = b;
            packedArgb[i] = 0xFF00_0000 | (r << 16) | (g << 8) | b;
        }
    }

    /** Matches {@code GrayscaleFilter}'s per-pixel arithmetic directly on packed ARGB. */
    @Benchmark
    public void scalarPacked(Blackhole blackhole) {
        for (int i = 0; i < packedArgb.length; i++) {
            int argb = packedArgb[i];
            int luma = (W_RED * ((argb >> 16) & 0xFF)
                    + W_GREEN * ((argb >> 8) & 0xFF)
                    + W_BLUE * (argb & 0xFF)) >>> SHIFT;
            grayOut[i] = luma;
        }
        blackhole.consume(grayOut);
    }

    /**
     * Same arithmetic, vectorized — but assumes the channels are already deinterleaved into
     * separate {@code int[]} arrays. Isolates the Vector API's arithmetic ceiling from the
     * layout-conversion cost measured by {@link #vectorWithDeinterleave}.
     */
    @Benchmark
    public void vectorPreSeparated(Blackhole blackhole) {
        int length = red.length;
        int upperBound = SPECIES.loopBound(length);
        int i = 0;
        for (; i < upperBound; i += SPECIES.length()) {
            IntVector r = IntVector.fromArray(SPECIES, red, i);
            IntVector g = IntVector.fromArray(SPECIES, green, i);
            IntVector b = IntVector.fromArray(SPECIES, blue, i);
            IntVector luma = r.mul(W_RED).add(g.mul(W_GREEN)).add(b.mul(W_BLUE))
                    .lanewise(VectorOperators.LSHR, SHIFT);
            luma.intoArray(grayOut, i);
        }
        for (; i < length; i++) {
            grayOut[i] = (W_RED * red[i] + W_GREEN * green[i] + W_BLUE * blue[i]) >>> SHIFT;
        }
        blackhole.consume(grayOut);
    }

    /**
     * Full production-equivalent cost: deinterleave packed ARGB into per-channel arrays (scalar —
     * bit-shift extraction doesn't vectorize on this layout) and then run the vectorized
     * arithmetic above. This is the number that matters for the "should we adopt this" decision,
     * not {@link #vectorPreSeparated} in isolation.
     */
    @Benchmark
    public void vectorWithDeinterleave(Blackhole blackhole) {
        int length = packedArgb.length;
        for (int i = 0; i < length; i++) {
            int argb = packedArgb[i];
            red[i] = (argb >> 16) & 0xFF;
            green[i] = (argb >> 8) & 0xFF;
            blue[i] = argb & 0xFF;
        }
        vectorPreSeparated(blackhole);
    }
}
