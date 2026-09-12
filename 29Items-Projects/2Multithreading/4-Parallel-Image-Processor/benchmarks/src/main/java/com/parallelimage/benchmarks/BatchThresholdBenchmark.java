package com.parallelimage.benchmarks;

import com.parallelimage.core.filter.BoxBlurFilter;
import com.parallelimage.core.filter.Pixels;
import com.parallelimage.core.fork.BatchProcessingTask;
import com.parallelimage.core.fork.CancellationToken;
import com.parallelimage.core.fork.JobProcessor;
import com.parallelimage.core.fork.TileProcessingAction;
import com.parallelimage.core.model.ImageJob;
import com.parallelimage.core.model.JobOutcome;
import com.parallelimage.core.model.ProcessingOptions;
import com.parallelimage.core.progress.ProgressListener;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
 * Calibrates {@code ProcessingOptions.DEFAULT_BATCH_THRESHOLD_JOBS} — the Level-1 (batch) leaf
 * size — by sweeping the threshold and the pool's parallelism against a fixed-size batch of
 * synthetic jobs.
 *
 * <p>Each job runs a real, but deliberately small, Level-2 {@link TileProcessingAction} tree
 * ({@link BoxBlurFilter} over a small in-memory image) instead of touching the filesystem, so this
 * benchmark isolates the Level-1 fork/join overhead rather than decode/encode I/O — the same
 * seam {@link com.parallelimage.core.fork.JobProcessor} exists to make testable.
 *
 * <p>Run: {@code mvn -pl benchmarks -am package -DskipTests && java -jar
 * benchmarks/target/benchmarks.jar BatchThresholdBenchmark -f 1 -wi 2 -i 3}. See
 * {@code docs/PERFORMANCE.md} for the recorded results.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
public class BatchThresholdBenchmark {

    private static final int JOB_COUNT = 64;
    private static final int JOB_IMAGE_SIZE = 512;
    private static final int BLUR_RADIUS = 3;
    private static final long TILE_THRESHOLD_PIXELS = ProcessingOptions.DEFAULT_TILE_THRESHOLD_PIXELS;

    /** Spans below and above the current default (8). */
    @Param({"1", "4", "8", "16", "32"})
    public int batchThresholdJobs;

    /** A 1..2N sweep for typical 8-16 logical core desktop/CI hardware. */
    @Param({"1", "2", "4", "8", "16"})
    public int parallelism;

    private List<ImageJob> jobs;
    private ForkJoinPool pool;
    private JobProcessor processor;

    @Setup(Level.Trial)
    public void setUp() {
        BufferedImage source = synthesizeImage(JOB_IMAGE_SIZE, JOB_IMAGE_SIZE);
        BoxBlurFilter filter = new BoxBlurFilter(BLUR_RADIUS);
        jobs = buildJobs(JOB_COUNT);
        pool = new ForkJoinPool(
                parallelism, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true);
        processor = job -> {
            long startNanos = System.nanoTime();
            BufferedImage target = Pixels.sameShape(source);
            TileProcessingAction action = TileProcessingAction.forWholeImage(
                    source, target, filter, TILE_THRESHOLD_PIXELS, CancellationToken.NONE);
            action.invoke();
            return new JobOutcome.Success(job.id(), job.target(), System.nanoTime() - startNanos,
                    (long) source.getWidth() * source.getHeight());
        };
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        pool.shutdown();
    }

    @Benchmark
    public void batchOfJobs(Blackhole blackhole) {
        BatchProcessingTask task = BatchProcessingTask.of(
                jobs, batchThresholdJobs, processor, CancellationToken.NONE, ProgressListener.NO_OP);
        blackhole.consume(pool.invoke(task));
    }

    private static List<ImageJob> buildJobs(int count) {
        ProcessingOptions options = ProcessingOptions.defaults();
        List<ImageJob> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(ImageJob.create("bench-batch",
                    Path.of("bench-source-" + i + ".png"),
                    Path.of("bench-target-" + i + ".png"),
                    options));
        }
        return result;
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
