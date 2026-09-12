package com.parallelimage.core.engine;

import com.parallelimage.core.error.PipelineException;
import com.parallelimage.core.fork.BatchProcessingTask;
import com.parallelimage.core.fork.CancellationToken;
import com.parallelimage.core.fork.ForkJoinConfig;
import com.parallelimage.core.io.ImageLoader;
import com.parallelimage.core.io.ImageSink;
import com.parallelimage.core.jfr.BatchProcessingEvent;
import com.parallelimage.core.metadata.MetadataStore;
import com.parallelimage.core.model.BatchResult;
import com.parallelimage.core.model.ImageJob;
import com.parallelimage.core.model.ImageMetadata;
import com.parallelimage.core.model.JobOutcome;
import com.parallelimage.core.model.ProcessingOptions;
import com.parallelimage.core.pipeline.OperationPipeline;
import com.parallelimage.core.port.JobRepository;
import com.parallelimage.core.progress.ProgressEvent;
import com.parallelimage.core.progress.ProgressListener;
import com.parallelimage.core.spi.ImageEnhancer;
import com.parallelimage.core.util.Preconditions;
import com.parallelimage.core.util.StopWatch;
import java.awt.image.BufferedImage;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The application's façade over the whole processing machine: it owns the pool, composes the
 * per-image work, submits the Level-1 task tree, and reports health.
 *
 * <p>Everything above it — the JavaFX view model, the CLI, the loopback HTTP controller — talks only
 * to this class. Everything below it (fork/join, ImageIO, StampedLock, the SPI) is an implementation
 * detail. That is the whole point of the hexagonal layout: three very different front ends drive one
 * engine, and none of them can accidentally depend on how tiles are split.
 *
 * <h2>The per-image sequence</h2>
 * <ol>
 *   <li>{@link JobRepository#markRunning} — so a crash from here on is a resumable {@code RUNNING}
 *       row, not a job silently stuck {@code PENDING} forever</li>
 *   <li>{@link ImageLoader#load} — header check, then decode, then normalize to an int-packed raster</li>
 *   <li>{@link MetadataStore#put} and {@link JobRepository#recordMetadata} — publish the facts</li>
 *   <li>{@link OperationPipeline#execute} — transform, forking Level-2 tile trees for tileable stages</li>
 *   <li>{@link ImageSink#write} — encode to a temp file, then atomically move into place</li>
 *   <li>{@link JobRepository#recordOutcome} — durable history</li>
 * </ol>
 * Steps 1–4 are pure per-job work with no shared mutable state except the metadata store, which is
 * built for exactly this contention pattern. That is why the engine needs no locks of its own.
 *
 * <h2>Why the engine converts {@link CancellationException} into an outcome</h2>
 * A kernel that notices the token mid-image throws {@code CancellationException}, and left alone it
 * would unwind the entire Level-1 tree — taking every already-merged {@link BatchResult} with it, so
 * a cancelled batch would report nothing at all. {@link #runJob} therefore catches it and returns
 * {@link JobOutcome.Cancelled}, letting the tree finish normally and drain in milliseconds (every
 * remaining leaf sees the flag and returns immediately). The user gets "41 done, 9 cancelled" rather
 * than an empty screen. {@code BatchProcessingTask}'s own rethrow stays in place as the backstop for
 * a cancellation raised outside a job processor.
 *
 * <h2>Threading</h2>
 * <strong>Thread-safe.</strong> {@link #process} is intended to be called from one caller at a time
 * (a batch at a time is the product model), but {@link #stats} and {@link #cancel} are explicitly
 * designed to be called concurrently from the JavaFX thread while a batch runs — that is what the
 * status bar and the Cancel button do.
 */
public final class ImageProcessingEngine implements AutoCloseable {

    private static final Logger LOG = System.getLogger(ImageProcessingEngine.class.getName());

    /** Watermark overlays are decorations, not payloads; refuse a "logo" that is itself a panorama. */
    private static final long MAX_OVERLAY_PIXELS = 16_000_000L;

    /** How long {@link #close()} waits for in-flight images to finish writing. */
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30L;

    private final ForkJoinPool pool;
    private final boolean ownsPool;
    private final MetadataStore metadataStore;
    private final JobRepository repository;
    private final ImageEnhancer enhancer;

    /**
     * Decoded watermark overlays, keyed by source path.
     *
     * <p>Without this cache the same 200&nbsp;KB PNG is decoded once per image in the batch. The
     * cached {@link BufferedImage}s are only ever <em>read</em> (composited onto a destination the
     * worker owns), and concurrent reads of a {@code BufferedImage} are safe, so no copy per worker is
     * needed. {@link Optional#empty()} is stored for a path that failed to decode: a
     * {@link ConcurrentHashMap} cannot hold {@code null}, and without a negative entry every image in
     * the batch would retry — and re-log — the same broken file.
     */
    private final Map<Path, Optional<BufferedImage>> overlayCache = new ConcurrentHashMap<>();

    /** The token of the batch currently running, so the UI's Cancel button has something to flip. */
    private final AtomicReference<CancellationToken> activeToken =
            new AtomicReference<>(CancellationToken.NONE);

    private volatile boolean closed;

    private ImageProcessingEngine(Builder builder) {
        this.metadataStore = builder.metadataStore == null ? new MetadataStore() : builder.metadataStore;
        this.repository = builder.repository == null ? JobRepository.NO_OP : builder.repository;
        this.enhancer = builder.enhancer == null ? ImageEnhancer.discover() : builder.enhancer;
        if (builder.pool != null) {
            this.pool = builder.pool;
            this.ownsPool = false;
        } else {
            this.pool = ForkJoinConfig.newPool(builder.parallelism);
            this.ownsPool = true;
        }
        LOG.log(Level.INFO, () -> "engine ready: " + ForkJoinConfig.describe(pool)
                + " enhancer=" + enhancer.describe());
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Defaults everywhere: own pool at {@link ForkJoinConfig#defaultParallelism()}, no history. */
    public static ImageProcessingEngine withDefaults() {
        return builder().build();
    }

    /**
     * Processes a batch and blocks until it finishes.
     *
     * <p>Never throws for a bad input file — a failure is a {@link JobOutcome.Failure} inside the
     * returned result. It <em>does</em> throw for a programming error (a null job list, a closed
     * engine) and it lets {@link Error} escape, because a JVM that is out of heap has nothing useful
     * left to report.
     *
     * @param jobs     jobs to run; all must share one {@code batchId}
     * @param listener progress sink, {@code null} for none
     * @return the merged result; {@link BatchResult#EMPTY} for an empty list
     */
    public BatchResult process(List<ImageJob> jobs, ProgressListener listener) {
        Preconditions.requireNonNull(jobs, "jobs");
        Preconditions.requireState(!closed, "engine is closed");
        if (jobs.isEmpty()) {
            return BatchResult.EMPTY;
        }

        ProgressListener sink = listener == null ? ProgressListener.NO_OP : listener;
        String batchId = jobs.get(0).batchId();
        int thresholdJobs = jobs.get(0).options().batchThresholdJobs();
        CancellationToken token = new CancellationToken();
        activeToken.set(token);

        repository.saveBatch(batchId, jobs);
        sink.onProgress(ProgressEvent.batchStarted(batchId, jobs.size()));
        StopWatch watch = StopWatch.started();

        BatchProcessingTask task = BatchProcessingTask.of(
                jobs, thresholdJobs, job -> runJob(job, token), token, sink);

        BatchProcessingEvent jfrEvent = new BatchProcessingEvent();
        jfrEvent.begin();
        BatchResult result;
        try {
            // invoke() from a non-worker thread: the caller blocks, the pool does the work. Calling
            // this from *inside* the pool would be a bug -- it would consume a worker as a waiter.
            result = pool.invoke(task);
        } catch (CancellationException e) {
            // Only reachable if cancellation was raised outside runJob's guard; partial counts are
            // already lost at that point, so synthesize a fully-cancelled result rather than lie.
            LOG.log(Level.INFO, () -> "batch " + batchId + " unwound on cancellation");
            result = cancelledResult(jobs, watch.elapsedNanos());
        }
        jfrEvent.end();
        if (jfrEvent.shouldCommit()) {
            jfrEvent.batchId = batchId;
            jfrEvent.jobCount = jobs.size();
            jfrEvent.succeeded = result.succeeded();
            jfrEvent.failed = result.failed();
            jfrEvent.cancelled = result.cancelled();
            jfrEvent.pixelsProcessed = result.pixelsProcessed();
            jfrEvent.commit();
        }

        long wallClockMillis = watch.elapsedMillis();
        repository.completeBatch(batchId, wallClockMillis);
        sink.onProgress(ProgressEvent.batchFinished(batchId, result.total(), jobs.size()));
        activeToken.set(CancellationToken.NONE);

        BatchResult finished = result;
        LOG.log(Level.INFO, () -> "batch %s finished in %d ms: %s (%.1f MP/s aggregate, %s)".formatted(
                batchId, wallClockMillis, finished,
                wallClockMillis == 0L ? 0.0d
                        : (finished.pixelsProcessed() / 1_000_000.0d) / (wallClockMillis / 1_000.0d),
                ForkJoinConfig.describe(pool)));
        return result;
    }

    /** Convenience overload for the CLI, which has nothing to draw a progress bar on. */
    public BatchResult process(List<ImageJob> jobs) {
        return process(jobs, ProgressListener.NO_OP);
    }

    /**
     * Requests cancellation of the running batch.
     *
     * <p>Safe to call from the JavaFX thread: it flips one {@link java.util.concurrent.atomic.AtomicBoolean}
     * and returns immediately rather than waiting for workers to notice.
     *
     * @return {@code true} if this call flipped the flag
     */
    public boolean cancel() {
        return activeToken.get().cancel();
    }

    /** Live pool and metadata counters. Poll this at a few hertz, never per image. */
    public EngineStats stats() {
        return EngineStats.sample(pool, metadataStore);
    }

    public MetadataStore metadata() {
        return metadataStore;
    }

    public ImageEnhancer enhancer() {
        return enhancer;
    }

    public int parallelism() {
        return pool.getParallelism();
    }

    /**
     * Runs one image end to end, converting every expected failure into a {@link JobOutcome}.
     *
     * <p>This is the {@code JobProcessor} handed to Level 1, and it is where the "failures are data"
     * rule is actually enforced: a corrupt JPEG in the middle of a 5 000-image batch must cost one
     * row in the results table, not the batch.
     */
    private JobOutcome runJob(ImageJob job, CancellationToken token) {
        StopWatch watch = StopWatch.started();
        ProcessingOptions options = job.options();
        try {
            token.throwIfCancelledOrInterrupted();

            if (!options.overwriteExisting() && Files.exists(job.target())) {
                // Deliberately a Success with zero pixels: the target exists and is correct, so the
                // batch is not "failed", but the throughput figures must not credit work we skipped.
                // This is what makes a re-run after a crash cheap (TECH-NOTES §3.6 E4).
                LOG.log(Level.DEBUG, () -> "skipping " + job.displayName() + ": target exists");
                return record(new JobOutcome.Success(job.id(), job.target(), watch.elapsedNanos(), 0L));
            }

            repository.markRunning(job.id());
            ImageLoader.Decoded decoded = ImageLoader.load(
                    job.id(), job.source(), options.maxPixelsPerImage());
            ImageMetadata metadata = decoded.metadata();
            metadataStore.put(metadata);
            repository.recordMetadata(metadata);

            OperationPipeline pipeline =
                    OperationPipeline.from(options, enhancer, this::overlay, token);
            BufferedImage processed = pipeline.execute(decoded.image());
            Path written = ImageSink.write(processed, job.target(), options);

            return record(new JobOutcome.Success(
                    job.id(), written, watch.elapsedNanos(), metadata.pixelCount()));
        } catch (CancellationException e) {
            return record(new JobOutcome.Cancelled(job.id(), watch.elapsedNanos()));
        } catch (PipelineException e) {
            // The only failure that knows which stage broke; keep that name, it is what a user needs.
            return record(JobOutcome.Failure.from(
                    job.id(), e, e.operationName(), watch.elapsedNanos()));
        } catch (RuntimeException e) {
            return record(JobOutcome.Failure.from(job.id(), e, null, watch.elapsedNanos()));
        }
        // No catch for Error. An OutOfMemoryError means the configured parallelism does not fit the
        // heap; swallowing it per image would turn one honest crash into thousands of mystery
        // failures (ARCHITECTURE §2.6 rule 4).
    }

    /**
     * Persists an outcome and returns it unchanged, so call sites read as {@code return record(...)}.
     *
     * <p>{@link JobRepository} contractually never throws, but this belt-and-braces catch means a
     * misbehaving adapter cannot turn a successfully written image into a reported failure.
     */
    private JobOutcome record(JobOutcome outcome) {
        try {
            repository.recordOutcome(outcome);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "job history write failed; output file is unaffected", e);
        }
        return outcome;
    }

    /** Decodes a watermark overlay at most once per batch. See {@link #overlayCache}. */
    private BufferedImage overlay(Path path) {
        if (path == null) {
            return null;
        }
        return overlayCache.computeIfAbsent(path, file -> {
            try {
                return Optional.of(ImageLoader.load("overlay", file, MAX_OVERLAY_PIXELS).image());
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, () -> "watermark overlay unreadable, falling back to text: "
                        + file.getFileName(), e);
                return Optional.empty();
            }
        }).orElse(null);
    }

    private static BatchResult cancelledResult(List<ImageJob> jobs, long elapsedNanos) {
        long perJob = jobs.isEmpty() ? 0L : elapsedNanos / jobs.size();
        BatchResult result = BatchResult.EMPTY;
        for (ImageJob job : jobs) {
            result = result.merge(BatchResult.of(new JobOutcome.Cancelled(job.id(), perJob)));
        }
        return result;
    }

    /**
     * Drains and shuts down the pool (only if this engine created it) and closes the repository.
     *
     * <p>Idempotent. The pool's workers are daemon threads, so a missed {@code close()} cannot hang
     * JVM exit — but skipping the drain leaves {@code *.tmp} files from images that were mid-encode.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        cancel();
        if (ownsPool) {
            ForkJoinConfig.shutdownGracefully(pool, SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        overlayCache.clear();
        try {
            repository.close();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "job repository failed to close cleanly", e);
        }
        LOG.log(Level.INFO, "engine closed");
    }

    /**
     * Assembles an engine.
     *
     * <p>Every dependency has a working default, so {@code builder().build()} produces a usable engine
     * with no database and no native library — which is exactly what the unit tests use.
     *
     * <p>Not thread-safe; build on one thread.
     */
    public static final class Builder {

        private ForkJoinPool pool;
        private int parallelism;
        private MetadataStore metadataStore;
        private JobRepository repository;
        private ImageEnhancer enhancer;

        private Builder() {
        }

        /** Supplies an external pool; the engine will <em>not</em> shut it down. */
        public Builder pool(ForkJoinPool value) {
            this.pool = value;
            return this;
        }

        /** Parallelism for the engine-owned pool; ignored when {@link #pool} is set. */
        public Builder parallelism(int value) {
            this.parallelism = value;
            return this;
        }

        public Builder metadataStore(MetadataStore value) {
            this.metadataStore = value;
            return this;
        }

        public Builder repository(JobRepository value) {
            this.repository = value;
            return this;
        }

        /** Overrides {@link ImageEnhancer#discover()}; useful for a deterministic test double. */
        public Builder enhancer(ImageEnhancer value) {
            this.enhancer = value;
            return this;
        }

        public ImageProcessingEngine build() {
            return new ImageProcessingEngine(this);
        }
    }
}
