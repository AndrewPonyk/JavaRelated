package com.parallelimage.app.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.app.config.AppConfig;
import com.parallelimage.app.wiring.ServiceRegistry;
import com.parallelimage.core.engine.ImageProcessingEngine;
import com.parallelimage.core.model.ImageJob;
import com.parallelimage.core.model.ImageMetadata;
import com.parallelimage.core.model.JobOutcome;
import com.parallelimage.core.model.JobStatus;
import com.parallelimage.core.pipeline.ImageOperation.EnhanceMode;
import com.parallelimage.core.port.JobRepository;
import com.parallelimage.core.spi.ImageEnhancer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link CliRunner} tests: every exit-code branch, the quiet/history/plural wording, and failure reporting. */
class CliRunnerTest {

    @TempDir
    private Path root;

    private final ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(outBuffer, true, StandardCharsets.UTF_8);
    private final PrintStream err = new PrintStream(errBuffer, true, StandardCharsets.UTF_8);

    private String out() {
        return outBuffer.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return errBuffer.toString(StandardCharsets.UTF_8);
    }

    private AppConfig config() {
        return AppConfig.load(root.resolve("does-not-exist.properties"));
    }

    private CliOptions options(Path input, Path output, String pipeline, boolean recursive, boolean quiet) {
        return new CliOptions(Optional.ofNullable(input), Optional.ofNullable(output), pipeline,
                Optional.empty(), Optional.empty(), 0, recursive, false, false, true, quiet, false, false,
                false);
    }

    private CliOptions dryRunOptions(Path input, Path output) {
        return new CliOptions(Optional.ofNullable(input), Optional.ofNullable(output), "",
                Optional.empty(), Optional.empty(), 0, false, false, false, true, false, true, false,
                false);
    }

    private CliRunner runnerFor(JobRepository repository) {
        ImageProcessingEngine engine = ImageProcessingEngine.builder()
                .repository(repository)
                .enhancer(new NoOpEnhancer())
                .build();
        ServiceRegistry.Registry registry =
                new ServiceRegistry.Registry(config(), engine, repository, "test-no-op-enhancer");
        return new CliRunner(registry, out, err);
    }

    private void writeImage(Path file) throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(image, "png", file.toFile());
    }

    private Path newDir(String name) throws IOException {
        return Files.createDirectories(root.resolve(name));
    }

    @Nested
    @DisplayName("startup failures")
    class StartupFailures {

        @Test
        @DisplayName("validation problems print each error and return EXIT_USAGE")
        void validationFailureReturnsUsage() {
            CliOptions bad = options(null, null, "", false, false);
            CliRunner runner = runnerFor(JobRepository.NO_OP);

            int code = runner.run(bad);

            assertEquals(CliRunner.EXIT_USAGE, code);
            assertTrue(err().contains("error: --in is required"), err());
            assertTrue(err().contains("run with --help for usage"), err());
        }

        @Test
        @DisplayName("a non-directory --in returns EXIT_STARTUP")
        void nonDirectoryInputReturnsStartup() throws IOException {
            Path missing = root.resolve("nope");
            Path output = root.resolve("out1");
            CliRunner runner = runnerFor(JobRepository.NO_OP);

            int code = runner.run(options(missing, output, "", false, false));

            assertEquals(CliRunner.EXIT_STARTUP, code);
            assertTrue(err().contains("error: --in is not a directory: " + missing), err());
        }

        @Test
        @DisplayName("an --out that cannot be created returns EXIT_STARTUP")
        void outputCreationFailureReturnsStartup() throws IOException {
            Path input = newDir("photos");
            Path blocker = root.resolve("blocker");
            Files.writeString(blocker, "not a directory");
            Path output = blocker.resolve("sub");
            CliRunner runner = runnerFor(JobRepository.NO_OP);

            int code = runner.run(options(input, output, "", false, false));

            assertEquals(CliRunner.EXIT_STARTUP, code);
            assertTrue(err().contains("error: cannot create --out directory " + output), err());
        }
    }

    @Nested
    @DisplayName("empty input directory")
    class EmptyInput {

        @Test
        @DisplayName("non-recursive prints the --recursive hint and returns EXIT_OK")
        void nonRecursiveHintsAtRecursiveFlag() throws IOException {
            Path input = newDir("photos");
            Path output = root.resolve("out");
            CliRunner runner = runnerFor(JobRepository.NO_OP);

            int code = runner.run(options(input, output, "", false, false));

            assertEquals(CliRunner.EXIT_OK, code);
            assertTrue(out().contains("no images found in " + input), out());
            assertTrue(out().contains("(add --recursive to include subdirectories)"), out());
        }

        @Test
        @DisplayName("recursive omits the hint")
        void recursiveOmitsHint() throws IOException {
            Path input = newDir("photos");
            Path output = root.resolve("out");
            CliRunner runner = runnerFor(JobRepository.NO_OP);

            int code = runner.run(options(input, output, "", true, false));

            assertEquals(CliRunner.EXIT_OK, code);
            assertTrue(out().contains("no images found in " + input), out());
            assertFalse(out().contains("--recursive"), out());
        }
    }

    @Nested
    @DisplayName("dry run")
    class DryRun {

        @Test
        @DisplayName("prints the planned job count and paths, writes nothing, returns EXIT_OK")
        void plansWithoutWriting() throws IOException {
            Path input = newDir("photos");
            writeImage(input.resolve("a.png"));
            writeImage(input.resolve("b.png"));
            Path output = root.resolve("out");
            CliRunner runner = runnerFor(JobRepository.NO_OP);

            int code = runner.run(dryRunOptions(input, output));

            assertEquals(CliRunner.EXIT_OK, code);
            assertTrue(out().contains("dry run: 2 jobs planned, nothing written"), out());
            assertTrue(out().contains(input.resolve("a.png").toString()), out());
            assertTrue(out().contains(input.resolve("b.png").toString()), out());
            assertFalse(Files.exists(output.resolve("a.png")), "dry run must not write output files");
            assertFalse(Files.exists(output.resolve("b.png")), "dry run must not write output files");
        }
    }

    @Nested
    @DisplayName("successful batches")
    class SuccessfulBatches {

        @Test
        @DisplayName("quiet mode prints only the summary, nothing to stderr")
        void quietPrintsSummaryOnly() throws IOException {
            Path input = newDir("photos");
            writeImage(input.resolve("a.png"));
            writeImage(input.resolve("b.png"));
            Path output = root.resolve("out");
            CliRunner runner = runnerFor(JobRepository.NO_OP);

            int code = runner.run(options(input, output, "", false, true));

            assertEquals(CliRunner.EXIT_OK, code);
            assertEquals("", err());
            assertTrue(out().contains("processed 2 of 2"), out());
            assertTrue(out().contains("ok: 2   failed: 0   cancelled: 0"), out());
        }

        @Test
        @DisplayName("a single image uses singular wording and omits '(history off)' when history is on")
        void singularWordingWithHistoryOn() throws IOException {
            Path input = newDir("photos");
            writeImage(input.resolve("a.png"));
            Path output = root.resolve("out");
            CliRunner runner = runnerFor(new FakeRepository());

            int code = runner.run(options(input, output, "", false, false));

            assertEquals(CliRunner.EXIT_OK, code);
            assertTrue(err().contains("processing 1 image with"), err());
            assertFalse(err().contains("images with"), err());
            assertFalse(err().contains("(history off)"), err());
            assertTrue(err().contains("pipeline: (none:"), err());
            assertTrue(out().contains("processed 1 of 1"), out());
        }

        @Test
        @DisplayName("multiple images use plural wording and '(history off)' when history is off")
        void pluralWordingWithHistoryOff() throws IOException {
            Path input = newDir("photos");
            writeImage(input.resolve("a.png"));
            writeImage(input.resolve("b.png"));
            Path output = root.resolve("out");
            CliRunner runner = runnerFor(JobRepository.NO_OP);

            int code = runner.run(options(input, output, "", false, false));

            assertEquals(CliRunner.EXIT_OK, code);
            assertTrue(err().contains("processing 2 images with"), err());
            assertTrue(err().contains("(history off)"), err());
        }

        @Test
        @DisplayName("a non-empty --pipeline is rendered by describePipeline")
        void nonEmptyPipelineIsDescribed() throws IOException {
            Path input = newDir("photos");
            writeImage(input.resolve("a.png"));
            Path output = root.resolve("out");
            CliRunner runner = runnerFor(JobRepository.NO_OP);

            int code = runner.run(options(input, output, "grayscale", false, false));

            assertEquals(CliRunner.EXIT_OK, code);
            assertTrue(err().contains("pipeline: grayscale"), err());
        }
    }

    @Nested
    @DisplayName("failing jobs")
    class FailingJobs {

        @Test
        @DisplayName("quiet mode lists failures and the history hint in the summary")
        void quietListsFailuresAndHistoryHint() throws IOException {
            Path input = newDir("photos");
            Files.writeString(input.resolve("bad.png"), "not actually a png");
            Path output = root.resolve("out");
            CliRunner runner = runnerFor(new FakeRepository());

            int code = runner.run(options(input, output, "", false, true));

            assertEquals(CliRunner.EXIT_FAILURES, code);
            assertEquals("", err());
            assertTrue(out().contains("ok: 0   failed: 1   cancelled: 0"), out());
            assertTrue(out().contains("failures:"), out());
            assertTrue(out().contains("full detail is in the history database"), out());
        }

        @Test
        @DisplayName("non-quiet mode prints the failure live and suppresses the duplicate listing")
        void nonQuietPrintsLiveFailureOnly() throws IOException {
            Path input = newDir("photos");
            Files.writeString(input.resolve("bad.png"), "not actually a png");
            Path output = root.resolve("out");
            CliRunner runner = runnerFor(JobRepository.NO_OP);

            int code = runner.run(options(input, output, "", false, false));

            assertEquals(CliRunner.EXIT_FAILURES, code);
            assertTrue(err().contains("FAILED"), err());
            assertTrue(out().contains("ok: 0   failed: 1   cancelled: 0"), out());
            assertFalse(out().contains("failures:"), out());
            assertFalse(out().contains("full detail"), out());
        }
    }

    private static final class NoOpEnhancer implements ImageEnhancer {
        @Override
        public BufferedImage enhance(BufferedImage source, EnhanceMode mode, double strength) {
            return source;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String describe() {
            return "test-no-op-enhancer";
        }
    }

    /** A distinct {@link JobRepository} instance (not {@code NO_OP}) so {@code historyEnabled()} is true. */
    private static final class FakeRepository implements JobRepository {
        @Override
        public void saveBatch(String batchId, List<ImageJob> jobs) {
            // intentionally empty
        }

        @Override
        public void recordOutcome(JobOutcome outcome) {
            // intentionally empty
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
        public void recordMetadata(ImageMetadata metadata) {
            // intentionally empty
        }

        @Override
        public void completeBatch(String batchId, long wallClockMillis) {
            // intentionally empty
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
            // intentionally empty
        }
    }
}
