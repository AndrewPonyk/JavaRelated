package com.example.pipeline.presentation.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.pipeline.application.PipelineReport;
import com.example.pipeline.infrastructure.config.PipelineConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * The wiring, end to end, through the one entry point that has no {@code System.exit} in it.
 *
 * <p>{@link PipelineApplication#run(PipelineConfig)} exists as a separate method precisely so
 * a test can drive the whole graph — generator, bounded queues, filter threads, fork/join
 * aggregation, poison-pill shutdown, sinks — and assert on the exit code and the file on disk
 * without killing the surefire JVM. Everything below is a real run: no mocks, no stubbed
 * stages, and a {@link TempDir} for the CSV so a wiring bug fails an assertion instead of
 * writing into the working tree.
 *
 * <p>The two tests that earn their keep are {@link #aCompleteRunWritesTheCsvAndExitsZero()},
 * which is the main user flow, and {@link #aConfiguredJdbcUrlIsRefusedBeforeAnythingStarts()},
 * which pins the fail-fast guard: the JDBC repository is an unimplemented seam, and wiring it
 * used to produce a run that did all its work correctly, wrote the CSV, and only then failed
 * on save — reporting FAILED and exit 1 for a run that had in fact succeeded.
 *
 * <p>Runs are deliberately tiny (a few hundred events, unlimited rate) so the suite stays
 * quick; the concurrency behaviour itself is pinned by the stage and orchestrator tests.
 * {@code System.out} and {@code System.err} are captured for the duration, because the
 * console sink and the report both print, and asserting on stderr is how the guard's message
 * is checked.
 */
@Timeout(60)
@DisplayName("PipelineApplication.run")
class PipelineApplicationTest {

    private static final String CSV_HEADER = "sensor_id,count,min_value,max_value,avg_value,sum_value";

    // Not private: JUnit refuses to inject into a private @TempDir field.
    @TempDir
    Path outputDir;

    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream capturedOut;
    private ByteArrayOutputStream capturedErr;

    @BeforeEach
    void captureConsole() {
        originalOut = System.out;
        originalErr = System.err;
        capturedOut = new ByteArrayOutputStream();
        capturedErr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(capturedErr, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreConsole() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    /**
     * A short run with every optional subsystem off: no rate limit, no dashboard, no control
     * plane. Those have their own tests, and leaving them on here would only add a port bind
     * and a timer to a test about the data path.
     */
    private PipelineConfig.Builder shortRun() {
        return PipelineConfig.builder()
                .eventCount(400L)
                .eventsPerSecond(0L)
                .durationSeconds(0L)
                .batchSize(16)
                .sensorCount(4)
                .randomSeed(20260818L)
                .queueCapacity(32)
                .consumerThreads(2)
                .aggregationParallelism(2)
                .sequentialThreshold(8)
                .shutdownTimeoutSeconds(20L)
                .pollTimeoutMillis(100L)
                .dashboardEnabled(false)
                .httpEnabled(false)
                .outputDir(outputDir.toString());
    }

    private String out() {
        return capturedOut.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return capturedErr.toString(StandardCharsets.UTF_8);
    }

    /**
     * The main user flow: generate, filter, aggregate, report. A green assertion here means
     * the poison-pill shutdown reached the aggregation stage and the sink ran, which is the
     * part no unit test can prove on its own.
     */
    @Test
    @DisplayName("a complete run exits 0 and leaves a parseable CSV report")
    void aCompleteRunWritesTheCsvAndExitsZero() throws IOException {
        int exitCode = PipelineApplication.run(shortRun().build());

        assertEquals(PipelineReport.EXIT_OK, exitCode, err());

        Path csv = outputDir.resolve(PipelineApplication.CSV_FILE_NAME);
        assertTrue(Files.exists(csv), "the run should have written " + csv);
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        assertEquals(CSV_HEADER, lines.get(0));
        assertTrue(lines.size() > 1, "the default threshold should let some readings through");
        for (String row : lines.subList(1, lines.size())) {
            assertEquals(6, row.split(",", -1).length, row);
        }
        // The console report is the operator's only feedback when logging is at INFO.
        assertTrue(out().contains("=== Pipeline report ==="), out());
        assertTrue(out().contains("result : OK"), out());
    }

    /**
     * The counters reconcile, which is a correctness property rather than a performance
     * note: every event the producer counted was either rejected by the filter or
     * aggregated, whatever the bounded queues and the two filter threads did in between.
     * {@code reconciled=false} means an event went missing, and no amount of throughput
     * makes that an acceptable run.
     */
    @Test
    @DisplayName("the stage counters reconcile, so no event went missing")
    void everyProducedEventIsAccountedFor() {
        assertEquals(PipelineReport.EXIT_OK, PipelineApplication.run(shortRun().build()), err());

        assertTrue(out().contains("reconciled=true"), out());
        assertTrue(out().contains("errors=0"), out());
        assertFalse(out().contains("result : FAILED"), out());
    }

    /**
     * A run where the threshold rejects everything is a successful run with an empty report,
     * not a failure. The header-only CSV matters: an absent file would be indistinguishable
     * from a crash, and an absent publish would hide a shutdown that never reached the sink.
     */
    @Test
    @DisplayName("a threshold that rejects every reading still exits 0 and writes a header-only CSV")
    void aFullyFilteredRunStillSucceeds() throws IOException {
        int exitCode = PipelineApplication.run(shortRun().filterThreshold(1.0e9).build());

        assertEquals(PipelineReport.EXIT_OK, exitCode, err());
        Path csv = outputDir.resolve(PipelineApplication.CSV_FILE_NAME);
        assertEquals(List.of(CSV_HEADER), Files.readAllLines(csv, StandardCharsets.UTF_8),
                "nothing passed the filter, so the report is a header and no rows");
    }

    /**
     * The fail-fast guard. Exit 2 rather than 1, because the configuration is unusable and
     * retrying it will fail identically — the distinction a CI job acts on.
     */
    @Test
    @DisplayName("a configured JDBC URL is refused with exit 2 before any thread starts")
    void aConfiguredJdbcUrlIsRefusedBeforeAnythingStarts() {
        PipelineConfig config = shortRun()
                .jdbcUrl("jdbc:postgresql://localhost:5432/pipeline")
                .jdbcUser("pipeline")
                .build();

        assertEquals(PipelineReport.EXIT_CONFIG, PipelineApplication.run(config));

        // The message has to name the key to unset and the alternative, because the operator
        // reading it configured something that looks entirely reasonable.
        assertTrue(err().contains(PipelineConfig.KEY_JDBC_URL), err());
        assertTrue(err().contains("not implemented"), err());
        assertTrue(err().contains("in-memory"), err());
        // Refused *before* anything started: no report was printed and no file was written.
        assertFalse(Files.exists(outputDir.resolve(PipelineApplication.CSV_FILE_NAME)),
                "the guard must run before the sink is wired");
        assertFalse(out().contains("=== Pipeline report ==="), out());
    }

    /**
     * A wiring failure is a failed run, not a stack trace on the terminal. An output
     * directory whose parent is a regular file cannot be created, so the sink's write fails;
     * the run reports it rather than propagating an {@link java.io.UncheckedIOException} out
     * of {@code main}.
     */
    @Test
    @DisplayName("an output directory that cannot be created is reported, not thrown")
    void anUnusableOutputDirectoryIsReported() throws IOException {
        Path blocker = Files.createFile(outputDir.resolve("not-a-directory"));

        int exitCode = PipelineApplication.run(
                shortRun().outputDir(blocker.resolve("nested").toString()).build());

        // Exit 1, not an exception: the aggregation stage counts a sink failure as an error
        // and the report says FAILED, which is what an operator can act on.
        assertEquals(PipelineReport.EXIT_FAILURE, exitCode, out() + err());
        assertTrue(out().contains("result : FAILED"), out());
    }

    /**
     * The dashboard is a daemon thread refreshing off the same metrics the report reads. It
     * is optional, so the branch that starts it is only exercised when it is on — and a
     * dashboard that outlived the pools, or one that threw on a half-stopped pipeline, would
     * show up here as a non-zero exit rather than in any dashboard unit test.
     */
    @Test
    @DisplayName("a run with the dashboard enabled still exits 0")
    void theDashboardDoesNotDisturbTheRun() {
        PipelineConfig config = shortRun()
                .dashboardEnabled(true)
                .dashboardIntervalMillis(20L)
                .build();

        assertEquals(PipelineReport.EXIT_OK, PipelineApplication.run(config), err());
        assertTrue(out().contains("result : OK"), out());
    }

    /**
     * Same for the control plane: {@code startControlPlane} binds a socket, and the server is
     * closed before the pools are torn down so no handler can observe a half-stopped
     * pipeline. Only a real run through {@code run} covers that ordering.
     */
    @Test
    @DisplayName("a run with the control plane enabled starts it, serves, and exits 0")
    void theControlPlaneStartsAndStopsWithTheRun() throws IOException {
        PipelineConfig config = shortRun()
                .httpEnabled(true)
                .httpPort(freePort())
                .httpToken("test-token-at-least-16-chars")
                .build();

        assertEquals(PipelineReport.EXIT_OK, PipelineApplication.run(config), err());
        assertTrue(out().contains("result : OK"), out());
    }

    /**
     * An ephemeral port the OS has just released. There is a theoretical window between the
     * probe closing and the control plane binding, but no fixed port is safe on a shared CI
     * runner either, and this at least cannot collide with a service that is already up.
     */
    private static int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    @Test
    @DisplayName("the queue and file names other components key off are stable")
    void publicConstantsAreStable() {
        // The queue names appear in metrics output and in the control plane's JSON, and the
        // file name is documented in the README, so a rename is an output-format change.
        assertEquals("aggregates.csv", PipelineApplication.CSV_FILE_NAME);
        assertEquals("raw", PipelineApplication.QUEUE_RAW);
        assertEquals("filtered", PipelineApplication.QUEUE_FILTERED);
    }
}
