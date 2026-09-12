package com.parallelimage.benchmarks;

import com.parallelimage.core.filter.BoxBlurFilter;
import com.parallelimage.core.filter.Pixels;
import com.parallelimage.core.fork.CancellationToken;
import com.parallelimage.core.fork.TileProcessingAction;
import java.awt.image.BufferedImage;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Calibrates {@code ProcessingOptions.DEFAULT_TILE_THRESHOLD_PIXELS} — the Level-2 (tile)
 * fork/join leaf size — by sweeping the threshold and the pool's parallelism against a fixed
 * synthetic image and a real {@link BoxBlurFilter} kernel.
 *
 * <p>Run: {@code mvn -pl benchmarks -am package -DskipTests && java -jar
 * benchmarks/target/benchmarks.jar TileThresholdBenchmark -f 1 -wi 2 -i 3}. See
 * {@code docs/PERFORMANCE.md} for the recorded results and how to re-run with a specific GC.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
public class TileThresholdBenchmark {

    private static final int IMAGE_WIDTH = 4096;
    private static final int IMAGE_HEIGHT = 4096;
    private static final int BLUR_RADIUS = 3;

    /** Spans two decades below and above the current default (65_536L). */
    @Param({"4096", "16384", "65536", "262144", "1048576"})
    public long tileThresholdPixels;

    /** A 1..2N sweep for typical 8-16 logical core desktop/CI hardware. */
    @Param({"1", "2", "4", "8", "16"})
    public int parallelism;

    private BufferedImage source;
    private BoxBlurFilter filter;
    private ForkJoinPool pool;

    @Setup(Level.Trial)
    public void setUp() {
        source = synthesizeImage(IMAGE_WIDTH, IMAGE_HEIGHT);
        filter = new BoxBlurFilter(BLUR_RADIUS);
        pool = new ForkJoinPool(
                parallelism, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        pool.shutdown();
    }

    @Benchmark
    public void tileBlur(Blackhole blackhole) {
        BufferedImage target = Pixels.sameShape(source);
        TileProcessingAction action = TileProcessingAction.forWholeImage(
                source, target, filter, tileThresholdPixels, CancellationToken.NONE);
        pool.invoke(action);
        blackhole.consume(target);
    }

    private static BufferedImage synthesizeImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] data = Pixels.data(image);
        for (int i = 0; i < data.length; i++) {
            data[i] = Pixels.pack(255, i & 0xFF, (i >>> 8) & 0xFF, (i >>> 16) & 0xFF);
        }
        return image;
    }
}
