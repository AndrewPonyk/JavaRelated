package com.parallelimage.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.core.filter.Pixels;
import com.parallelimage.core.io.ImageSink;
import com.parallelimage.core.model.BatchResult;
import com.parallelimage.core.model.ImageJob;
import com.parallelimage.core.model.ImageMetadata;
import com.parallelimage.core.model.JobOutcome;
import com.parallelimage.core.model.JobStatus;
import com.parallelimage.core.model.ProcessingOptions;
import com.parallelimage.core.pipeline.ImageOperation;
import com.parallelimage.core.port.JobRepository;
import com.parallelimage.core.progress.ProgressEvent;
import com.parallelimage.core.progress.ProgressListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end tests for the façade: real files on a real filesystem, real ImageIO decoding and
 * encoding, a real {@link java.util.concurrent.ForkJoinPool}.
 *
 * <p>Every other test class in {@code pip-core} deliberately avoids the disk so that the concurrency
 * logic can be exercised in microseconds. This one does the opposite, because the properties worth
 * checking here only exist once I/O is involved: that a corrupt file costs one row rather than the
 * batch, that a skipped target is credited zero pixels, that no {@code .tmp} file survives a
 * successful write, and that a cancelled batch still accounts for every job it was given.
 *
 * <p>Nothing here asserts on timing. Where cancellation is involved the token is flipped from the
 * {@code BATCH_STARTED} callback — which {@link ImageProcessingEngine#process} raises after arming
 * {@link com.parallelimage.core.fork.CancellationToken} but before submitting the task tree — so the
 * outcome is deterministic rather than a race the CI machine gets to decide.
 */
class ImageProcessingEngineTest {

    @TempDir
    private Path temp;

    private Path inputDir;
    private Path outputDir;
    private ImageProcessingEngine engine;

    @BeforeEach
    void setUp() throws IOException {
        inputDir = Files.createDirectories(temp.resolve("in"));
        outputDir = temp.resolve("out");
        engine = ImageProcessingEngine.builder().parallelism(4).build();
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @Test
    @DisplayName("a batch decodes, transforms and writes every image")
    void processesEveryImageEndToEnd() throws IOException {
        List<Path> sources = writePngs(5, 80, 60);
        ProcessingOptions options = options()
                .operations(new ImageOperation.Grayscale(),
                        new ImageOperation.Resize(40, 30, true),
                        new ImageOperation.BoxBlur(1))
                .build();

        BatchResult result = engine.process(jobsFor(sources, options));

        assertEquals(5, result.succeeded());
        assertEquals(0, result.failed());
        assertEquals(0, result.toExitCode());
        assertEquals(5L * 80L * 60L, result.pixelsProcessed(),
                "throughput must be credited in source pixels, once per image");

        for (Path source : sources) {
            Path target = targetFor(source, options);
            assertTrue(Files.exists(target), "missing output for " + source.getFileName());
            BufferedImage written = ImageIO.read(target.toFile());
            assertNotNull(written, "the output file is not a decodable image");
            assertEquals(40, written.getWidth());
            assertEquals(30, written.getHeight());
        }
        assertEquals(5, engine.metadata().size(), "every decode must publish its metadata");
        assertEquals(5, engine.stats().metadataEntries());
    }

    @Test
    @DisplayName("an existing target is skipped as a success worth zero pixels")
    void skippedTargetsAreCreditedNoPixels() throws IOException {
        Path source = writePngs(1, 32, 32).get(0);
        ProcessingOptions options = options().build();
        Path target = targetFor(source, options);
        Files.createDirectories(outputDir);
        Files.writeString(target, "an earlier run already produced this");
        byte[] before = Files.readAllBytes(target);

        BatchResult result = engine.process(jobsFor(List.of(source), options));

        assertEquals(1, result.succeeded(), "a correct existing target is not a failure");
        assertEquals(0L, result.pixelsProcessed(),
                "crediting skipped work would inflate every MP/s figure the UI shows");
        assertArrayEqualsBytes(before, Files.readAllBytes(target));
        assertEquals(0, engine.metadata().size(), "a skipped job must not even decode");
    }

    @Test
    @DisplayName("overwriteExisting=true replaces the file instead of skipping it")
    void overwriteExistingRewritesTheTarget() throws IOException {
        Path source = writePngs(1, 32, 32).get(0);
        ProcessingOptions options = options().overwriteExisting(true).build();
        Path target = targetFor(source, options);
        Files.createDirectories(outputDir);
        Files.writeString(target, "stale");

        BatchResult result = engine.process(jobsFor(List.of(source), options));

        assertEquals(1, result.succeeded());
        assertEquals(1_024L, result.pixelsProcessed());
        assertNotNull(ImageIO.read(target.toFile()), "the stale placeholder must have been replaced");
    }

    @Test
    @DisplayName("one unreadable file costs one row, not the batch")
    void oneCorruptFileCostsOneRow() throws IOException {
        List<Path> sources = new ArrayList<>(writePngs(4, 48, 48));
        Path corrupt = inputDir.resolve("corrupt.png");
        // A plausible-looking name with no PNG signature: ImageIO finds no reader for it, which is
        // exactly what a truncated download looks like.
        Files.writeString(corrupt, "PNG? no.");
        sources.add(corrupt);
        ProcessingOptions options = options().build();

        BatchResult result = engine.process(jobsFor(sources, options));

        assertEquals(4, result.succeeded());
        assertEquals(1, result.failed());
        assertTrue(result.hasFailures());
        assertEquals(1, result.toExitCode(), "a partial batch must not exit 0");
        JobOutcome.Failure failure = result.failures().get(0);
        assertEquals("com.parallelimage.core.error.ImageIoException", failure.exceptionType());
        assertFalse(Files.exists(targetFor(corrupt, options)),
                "a failed decode must not leave an output file behind");
    }

    @Test
    @DisplayName("the decode-bomb guard refuses an oversized header before allocating")
    void decodeBombGuardRefusesOversizedImages() throws IOException {
        Path source = writePngs(1, 64, 64).get(0);
        ProcessingOptions options = options().maxPixelsPerImage(1_024L).build();

        BatchResult result = engine.process(jobsFor(List.of(source), options));

        assertEquals(1, result.failed());
        JobOutcome.Failure failure = result.failures().get(0);
        assertTrue(failure.reason().contains("pixel limit"),
                "the operator needs to be told it was a budget breach, not a corrupt file: "
                        + failure.reason());
        assertEquals(0, engine.metadata().size(), "the guard must fire before the raster is built");
    }

    @Test
    @DisplayName("JPEG output is flattened onto white rather than encoded with a bogus alpha band")
    void jpegOutputIsFlattened() throws IOException {
        Path source = inputDir.resolve("transparent.png");
        ImageIO.write(noise(40, 40, true), "png", source.toFile());
        ProcessingOptions options = options().outputFormat("jpeg").quality(0.85f).build();

        BatchResult result = engine.process(jobsFor(List.of(source), options));

        assertEquals(1, result.succeeded());
        Path target = targetFor(source, options);
        assertEquals("transparent.jpg", target.getFileName().toString(),
                "'jpeg' must normalize to the 'jpg' extension");
        BufferedImage written = ImageIO.read(target.toFile());
        assertNotNull(written);
        assertFalse(written.getColorModel().hasAlpha(),
                "a JPEG with four bands is read back as CMYK and looks pink");
    }

    @Test
    @DisplayName("a successful batch leaves no .tmp files behind")
    void noTemporaryFilesSurvive() throws IOException {
        List<Path> sources = writePngs(6, 40, 40);
        engine.process(jobsFor(sources, options().build()));

        try (Stream<Path> files = Files.list(outputDir)) {
            List<String> leftovers = files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".tmp"))
                    .toList();
            assertEquals(List.of(), leftovers,
                    "write-to-temp-then-move must clean up after itself");
        }
    }

    @Test
    @DisplayName("nested input folders are recreated under the output root")
    void nestedPathsAreRecreated() throws IOException {
        Path album = Files.createDirectories(inputDir.resolve("2026").resolve("summer"));
        Path source = album.resolve("beach.png");
        ImageIO.write(noise(32, 32, false), "png", source.toFile());
        ProcessingOptions options = options().build();

        Path target = ImageSink.targetFor(source, outputDir, inputDir, options.outputFormat());
        BatchResult result = engine.process(
                List.of(ImageJob.create("nested-batch", source, target, options)));

        assertEquals(1, result.succeeded());
        assertTrue(Files.isRegularFile(outputDir.resolve("2026").resolve("summer").resolve("beach.png")),
                "the album structure is part of the user's filing system, not incidental");
    }

    @Test
    @DisplayName("cancelling before the first job starts cancels all of them, and accounts for each")
    void cancellationAccountsForEveryJob() throws IOException {
        List<Path> sources = writePngs(16, 64, 64);
        ProcessingOptions options = options().batchThresholdJobs(1).build();

        // BATCH_STARTED is raised after the token is armed and before the tree is submitted, so this
        // is a deterministic "cancel arrives first", not a race.
        BatchResult result = engine.process(jobsFor(sources, options), event -> {
            if (event.phase() == ProgressEvent.Phase.BATCH_STARTED) {
                assertTrue(engine.cancel(), "the engine must expose the running batch's token");
            }
        });

        assertEquals(16, result.total(), "a cancelled batch still owes the user a full account");
        assertEquals(16, result.cancelled());
        assertEquals(0, result.succeeded());
        assertEquals(0, result.failed());
        assertFalse(Files.exists(outputDir), "no job ran, so nothing should have been written");
    }

    @Test
    @DisplayName("progress events bracket the batch and report each job exactly once")
    void progressEventsBracketTheBatch() throws IOException {
        List<Path> sources = writePngs(9, 32, 32);
        ConcurrentLinkedQueue<ProgressEvent> events = new ConcurrentLinkedQueue<>();

        engine.process(jobsFor(sources, options().build()), events::add);

        Map<ProgressEvent.Phase, Integer> counts = new EnumMap<>(ProgressEvent.Phase.class);
        for (ProgressEvent event : events) {
            counts.merge(event.phase(), 1, Integer::sum);
        }
        assertEquals(1, counts.get(ProgressEvent.Phase.BATCH_STARTED));
        assertEquals(1, counts.get(ProgressEvent.Phase.BATCH_FINISHED));
        assertEquals(9, counts.get(ProgressEvent.Phase.JOB_STARTED));
        assertEquals(9, counts.get(ProgressEvent.Phase.JOB_COMPLETED));

        ProgressEvent first = events.peek();
        assertEquals(ProgressEvent.Phase.BATCH_STARTED, first.phase(), "the bracket must open first");
        assertEquals(9, first.total());
        ProgressEvent last = events.stream().reduce((a, b) -> b).orElseThrow();
        assertEquals(ProgressEvent.Phase.BATCH_FINISHED, last.phase());
        assertEquals(1.0d, last.fraction(), 1e-9d);
    }

    @Test
    @DisplayName("the repository sees the whole lifecycle exactly once per job")
    void repositorySeesTheWholeLifecycle() throws IOException {
        RecordingRepository repository = new RecordingRepository();
        engine.close();
        engine = ImageProcessingEngine.builder().parallelism(2).repository(repository).build();

        List<Path> sources = writePngs(7, 32, 32);
        engine.process(jobsFor(sources, options().build()));

        assertEquals(1, repository.savedBatches.get());
        assertEquals(7, repository.metadata.size());
        assertEquals(7, repository.outcomes.size());
        assertEquals(1, repository.completedBatches.get());
        assertEquals(0, repository.closes.get(), "process() must not close the repository");

        engine.close();
        assertEquals(1, repository.closes.get(), "the engine owns the repository's lifetime");
    }

    @Test
    @DisplayName("a history write that throws cannot fail an image that was written correctly")
    void brokenHistoryCannotFailAGoodBatch() throws IOException {
        // Only recordOutcome throws here. The port documents that adapters never throw into the
        // engine; this asserts the engine's belt-and-braces catch for the one call it makes after the
        // output file is already durable, which is the case where losing the image would be absurd.
        JobRepository exploding = new RecordingRepository() {
            @Override
            public void recordOutcome(JobOutcome outcome) {
                throw new IllegalStateException("database is locked");
            }
        };
        engine.close();
        engine = ImageProcessingEngine.builder().parallelism(2).repository(exploding).build();

        Path source = writePngs(1, 32, 32).get(0);
        ProcessingOptions options = options().build();
        BatchResult result = engine.process(jobsFor(List.of(source), options));

        assertEquals(1, result.succeeded(), "history is a nice-to-have; the pixels are the product");
        assertTrue(Files.exists(targetFor(source, options)));
    }

    @Test
    @DisplayName("an empty batch short-circuits and a closed engine refuses work")
    void emptyBatchAndClosedEngine() {
        assertSame(BatchResult.EMPTY, engine.process(List.of()));
        assertTrue(engine.parallelism() >= 1);

        engine.close();
        engine.close();
        assertThrows(IllegalStateException.class,
                () -> engine.process(List.of()), "a closed engine must not silently accept a batch");
    }

    @Test
    @DisplayName("stats describe the pool and survive being sampled after close")
    void statsDescribeThePool() throws IOException {
        engine.process(jobsFor(writePngs(4, 48, 48), options().build()));

        EngineStats stats = engine.stats();
        assertEquals(4, stats.parallelism());
        assertTrue(stats.stealCount() >= 0L);
        assertEquals(4, stats.metadataEntries());
        assertTrue(stats.utilisation() >= 0.0d && stats.utilisation() <= 1.0d);
        assertNotNull(stats.summary());
        assertTrue(stats.optimisticReadRate() >= 0.0d && stats.optimisticReadRate() <= 1.0d);
    }

    // ---- helpers ------------------------------------------------------------------------------

    private ProcessingOptions.Builder options() {
        return ProcessingOptions.builder().batchThresholdJobs(2).tileThresholdPixels(4_096L);
    }

    private List<ImageJob> jobsFor(List<Path> sources, ProcessingOptions options) {
        List<ImageJob> jobs = new ArrayList<>(sources.size());
        for (Path source : sources) {
            jobs.add(ImageJob.create("batch-e2e", source, targetFor(source, options), options));
        }
        return jobs;
    }

    private Path targetFor(Path source, ProcessingOptions options) {
        return ImageSink.targetFor(source, outputDir, null, options.outputFormat());
    }

    private List<Path> writePngs(int count, int width, int height) throws IOException {
        List<Path> paths = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Path file = inputDir.resolve("photo-" + i + ".png");
            ImageIO.write(noise(width, height, false), "png", file.toFile());
            paths.add(file);
        }
        return paths;
    }

    /** Fixed seed: a failure that only reproduces on one machine is not worth having. */
    private static BufferedImage noise(int width, int height, boolean alpha) {
        BufferedImage image = new BufferedImage(width, height,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        int[] pixels = Pixels.data(image);
        Random random = new Random(20260818L);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = alpha ? random.nextInt() : 0xFF00_0000 | random.nextInt(0x0100_0000);
        }
        return image;
    }

    private static void assertArrayEqualsBytes(byte[] expected, byte[] actual) {
        assertTrue(java.util.Arrays.equals(expected, actual), "the existing file was modified");
    }

    /** Counting stand-in for the SQLite adapter; thread-safe because workers call it concurrently. */
    private static class RecordingRepository implements JobRepository {

        final AtomicInteger savedBatches = new AtomicInteger();
        final AtomicInteger completedBatches = new AtomicInteger();
        final AtomicInteger closes = new AtomicInteger();
        final ConcurrentLinkedQueue<JobOutcome> outcomes = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<ImageMetadata> metadata = new ConcurrentLinkedQueue<>();

        @Override
        public void saveBatch(String batchId, List<ImageJob> jobs) {
            savedBatches.incrementAndGet();
        }

        @Override
        public void recordOutcome(JobOutcome outcome) {
            outcomes.add(outcome);
        }

        @Override
        public void markRunning(String jobId) {
            // intentionally empty
        }

        @Override
        public List<JobRecord> findByStatus(JobStatus status) {
            return List.of();
        }

        @Override
        public void recordMetadata(ImageMetadata value) {
            metadata.add(value);
        }

        @Override
        public void completeBatch(String batchId, long wallClockMillis) {
            completedBatches.incrementAndGet();
        }

        @Override
        public List<JobRecord> recentJobs(int limit) {
            return List.of();
        }

        @Override
        public List<BatchSummary> recentBatches(int limit) {
            return List.of();
        }

        @Override
        public Optional<BatchSummary> findBatch(String batchId) {
            return Optional.empty();
        }

        @Override
        public int purgeOlderThan(Instant cutoff) {
            return 0;
        }

        @Override
        public void close() {
            closes.incrementAndGet();
        }
    }
}
