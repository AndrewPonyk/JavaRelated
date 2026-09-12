package com.example.pipeline.infrastructure.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.pipeline.domain.AggregateResult;
import com.example.pipeline.domain.AggregateSnapshot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link CsvAggregateSink}.
 *
 * <p>The sink is the only place in this process where configuration turns into a
 * filesystem write, so the containment check — a configured file name may not escape the
 * configured output directory — is the branch worth the most attention here. It is also
 * pure: no threads, no queues, no clock, which is why these tests assert on exact file
 * content rather than on tolerances.
 *
 * <p>Everything writes under a {@link TempDir}, so a bug that defeats containment fails
 * the assertion instead of writing somewhere real.
 */
class CsvAggregateSinkTest {

    private static final String HEADER = "sensor_id,count,min_value,max_value,avg_value,sum_value";

    // Not private: JUnit refuses to inject into a private @TempDir field.
    @TempDir
    Path outputDir;

    @Nested
    @DisplayName("path containment")
    class Containment {

        /**
         * The traversal attempt this check exists for. {@code ..} is the whole point: a
         * file name arriving from {@code application.properties}, an environment variable
         * or a CLI flag is operator input, and {@code Path.resolve} would follow it out of
         * the output root without complaint.
         */
        @ParameterizedTest
        @ValueSource(strings = {
            "../escaped.csv",
            "../../escaped.csv",
            "nested/../../escaped.csv",
            "./../escaped.csv",
        })
        @DisplayName("a file name that escapes the output directory is rejected")
        void rejectsTraversal(String fileName) {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> new CsvAggregateSink(outputDir.toString(), fileName));

            // The message has to name both the root and what the name resolved to, or an
            // operator staring at a rejected config has nothing to go on.
            assertTrue(thrown.getMessage().contains(outputDir.toAbsolutePath().normalize().toString()),
                    thrown.getMessage());
        }

        /**
         * Rejection happens in the constructor, not on the first {@code publish}. A
         * misconfigured path should fail while the pipeline is being wired, not after a
         * twenty-minute run has finished and the report is being written.
         */
        @Test
        @DisplayName("containment is checked at construction, before any run has happened")
        void rejectsEagerly() {
            assertThrows(IllegalArgumentException.class,
                    () -> new CsvAggregateSink(outputDir.toString(), "../escaped.csv"));

            assertTrue(Files.notExists(outputDir.resolveSibling("escaped.csv")),
                    "nothing may be created by a rejected configuration");
        }

        @Test
        @DisplayName("a nested name inside the output directory is allowed and its directories are created")
        void allowsNestedNames() {
            CsvAggregateSink sink = new CsvAggregateSink(outputDir.toString(), "reports/run-1/aggregates.csv");

            assertEquals(outputDir.resolve("reports/run-1/aggregates.csv").toAbsolutePath().normalize(),
                    sink.outputFile());

            sink.publish(snapshot(new AggregateResult("sensor-0", 1L, 2.0, 2.0, 2.0)));

            assertTrue(Files.isRegularFile(sink.outputFile()), "the parent directories should have been created");
        }

        @Test
        @DisplayName("rejects nulls rather than writing to a path built from \"null\"")
        void rejectsNulls() {
            assertThrows(NullPointerException.class, () -> new CsvAggregateSink(null, "out.csv"));
            assertThrows(NullPointerException.class, () -> new CsvAggregateSink(outputDir.toString(), null));

            CsvAggregateSink sink = new CsvAggregateSink(outputDir.toString(), "out.csv");
            assertThrows(NullPointerException.class, () -> sink.publish(null));
        }
    }

    @Nested
    @DisplayName("writing")
    class Writing {

        /**
         * Exact content, including the header and the row order. The sink's own contract
         * is that rows come out sorted by sensor id, which is what makes two runs of the
         * same seeded pipeline produce byte-identical files — a property worth having
         * because it turns "did my change perturb the data path?" into a diff.
         */
        @Test
        @DisplayName("writes a header and one row per sensor, sorted by sensor id")
        void writesSortedRows() throws IOException {
            CsvAggregateSink sink = new CsvAggregateSink(outputDir.toString(), "aggregates.csv");

            // Inserted out of order on purpose: a sink that preserved map order would
            // pass a two-row test and fail here.
            sink.publish(snapshot(
                    new AggregateResult("sensor-2", 2L, 1.0, 3.0, 4.0),
                    new AggregateResult("sensor-0", 3L, -1.5, 4.5, 6.0),
                    new AggregateResult("sensor-1", 1L, 7.25, 7.25, 7.25)));

            List<String> lines = Files.readAllLines(sink.outputFile(), StandardCharsets.UTF_8);

            assertEquals(List.of(
                    HEADER,
                    "sensor-0,3,-1.500000,4.500000,2.000000,6.000000",
                    "sensor-1,1,7.250000,7.250000,7.250000,7.250000",
                    "sensor-2,2,1.000000,3.000000,2.000000,4.000000"),
                    lines);
        }

        /**
         * A run where nothing survived the filter still produces a valid CSV with just a
         * header. Writing no file at all would be indistinguishable from a crashed run.
         */
        @Test
        @DisplayName("an empty snapshot still writes a header-only file")
        void writesHeaderForEmptySnapshot() throws IOException {
            CsvAggregateSink sink = new CsvAggregateSink(outputDir.toString(), "empty.csv");

            sink.publish(AggregateSnapshot.empty());

            assertEquals(List.of(HEADER), Files.readAllLines(sink.outputFile(), StandardCharsets.UTF_8));
        }

        /**
         * Republishing truncates rather than appends. The sink writes once at the end of a
         * run, so an append would silently accumulate stale runs into one file that still
         * parses as CSV — the worst kind of wrong.
         */
        @Test
        @DisplayName("a second publish replaces the file instead of appending to it")
        void overwritesOnRepublish() throws IOException {
            CsvAggregateSink sink = new CsvAggregateSink(outputDir.toString(), "aggregates.csv");

            sink.publish(snapshot(
                    new AggregateResult("sensor-0", 1L, 1.0, 1.0, 1.0),
                    new AggregateResult("sensor-1", 1L, 2.0, 2.0, 2.0)));
            sink.publish(snapshot(new AggregateResult("sensor-9", 1L, 3.0, 3.0, 3.0)));

            List<String> lines = Files.readAllLines(sink.outputFile(), StandardCharsets.UTF_8);

            assertEquals(2, lines.size(), lines.toString());
            assertEquals("sensor-9,1,3.000000,3.000000,3.000000,3.000000", lines.get(1));
        }

        /**
         * The row format is locale-independent. On a comma-decimal locale a
         * {@code String.format} without {@link java.util.Locale#ROOT} writes
         * {@code 2,000000}, which shifts every subsequent column — a CSV that parses
         * cleanly and means something else entirely.
         */
        @Test
        @DisplayName("decimal separators are dots, whatever the default locale is")
        void formatsIndependentlyOfLocale() throws IOException {
            CsvAggregateSink sink = new CsvAggregateSink(outputDir.toString(), "aggregates.csv");

            sink.publish(snapshot(new AggregateResult("sensor-0", 4L, 0.5, 2.5, 6.0)));

            String row = Files.readAllLines(sink.outputFile(), StandardCharsets.UTF_8).get(1);

            assertEquals(6, row.split(",", -1).length, row);
            assertEquals("sensor-0,4,0.500000,2.500000,1.500000,6.000000", row);
        }

        /**
         * A sensor id containing a comma would need quoting, which this sink deliberately
         * does not do — so it refuses rather than writing a row with a shifted column
         * count. Generated ids are {@code sensor-NN}, so this can only fire once ids
         * become externally supplied, which is exactly when someone needs to be told.
         */
        @Test
        @DisplayName("a sensor id that would need CSV quoting is refused, not silently written")
        void refusesIdsNeedingQuoting() {
            CsvAggregateSink sink = new CsvAggregateSink(outputDir.toString(), "aggregates.csv");
            AggregateSnapshot dangerous = snapshot(new AggregateResult("sensor,0", 1L, 1.0, 1.0, 1.0));

            assertThrows(IllegalArgumentException.class, () -> sink.publish(dangerous));
        }
    }

    /**
     * The write-to-temp-then-atomic-move path, from the reader's side.
     *
     * <p>The invariant is that a reader only ever sees a complete file or the last
     * complete file. Two things follow, and neither is visible from the happy-path tests
     * above: a successful write must not leave its temporary file behind (one stray
     * {@code .tmp} per run would accumulate in the output directory), and a write that
     * fails must leave the previous report exactly as it was rather than a truncated file
     * that still parses as CSV.
     */
    @Nested
    @DisplayName("crash safety")
    class CrashSafety {

        @Test
        @DisplayName("a successful publish leaves no temporary file behind")
        void successLeavesNoTempFile() throws IOException {
            CsvAggregateSink sink = new CsvAggregateSink(outputDir.toString(), "aggregates.csv");

            sink.publish(snapshot(new AggregateResult("sensor-0", 1L, 1.0, 1.0, 1.0)));

            assertTrue(Files.exists(sink.outputFile()));
            assertEquals(List.of(), tempFiles(outputDir), "the temporary file must be moved, not copied");
        }

        @Test
        @DisplayName("repeated publishes do not accumulate temporary files")
        void repeatedPublishesLeaveNoTempFiles() throws IOException {
            CsvAggregateSink sink = new CsvAggregateSink(outputDir.toString(), "aggregates.csv");

            for (int i = 0; i < 3; i++) {
                sink.publish(snapshot(new AggregateResult("sensor-" + i, 1L, 1.0, 1.0, 1.0)));
            }

            // The temp name is unique per call, so a missing cleanup would show up as three
            // files here rather than one overwritten one.
            assertEquals(List.of(), tempFiles(outputDir));
            try (var entries = Files.list(outputDir)) {
                assertEquals(1L, entries.count(), "only the report itself should remain");
            }
        }

        /**
         * The bug this ordering exists to prevent: rendering the rows after opening the
         * target meant a single unrenderable sensor id truncated a good report on its way
         * to throwing. The exception is the same either way; the file on disk is not.
         */
        @Test
        @DisplayName("a row rejected during rendering leaves the previous report intact")
        void aRejectedRowDoesNotDestroyTheLastGoodReport() throws IOException {
            CsvAggregateSink sink = new CsvAggregateSink(outputDir.toString(), "aggregates.csv");
            sink.publish(snapshot(new AggregateResult("sensor-0", 1L, 1.0, 1.0, 1.0)));
            List<String> good = Files.readAllLines(sink.outputFile(), StandardCharsets.UTF_8);

            AggregateSnapshot dangerous = snapshot(
                    new AggregateResult("sensor-1", 1L, 2.0, 2.0, 2.0),
                    new AggregateResult("sensor,2", 1L, 3.0, 3.0, 3.0));
            assertThrows(IllegalArgumentException.class, () -> sink.publish(dangerous));

            assertEquals(good, Files.readAllLines(sink.outputFile(), StandardCharsets.UTF_8),
                    "the failed publish must not have touched the target");
            assertEquals(List.of(), tempFiles(outputDir), "and must not have left a partial file");
        }

        @Test
        @DisplayName("a rejected row on the first publish writes nothing at all")
        void aRejectedRowOnAFirstPublishCreatesNoFile() throws IOException {
            CsvAggregateSink sink = new CsvAggregateSink(outputDir.toString(), "aggregates.csv");
            AggregateSnapshot dangerous = snapshot(new AggregateResult("sensor\"0", 1L, 1.0, 1.0, 1.0));

            assertThrows(IllegalArgumentException.class, () -> sink.publish(dangerous));

            // An empty report is worse than an absent one: it reads as "the run produced
            // nothing" rather than "the run failed".
            assertFalse(Files.exists(sink.outputFile()));
            assertEquals(List.of(), tempFiles(outputDir));
        }
    }

    /** Names of any leftover in-progress files directly under {@code dir}, sorted. */
    private static List<String> tempFiles(Path dir) throws IOException {
        try (var entries = Files.list(dir)) {
            return entries.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".tmp"))
                    .sorted()
                    .toList();
        }
    }

    /** Snapshot from the given aggregates, keyed by sensor id in argument order. */
    private static AggregateSnapshot snapshot(AggregateResult... results) {
        Map<String, AggregateResult> byId = new LinkedHashMap<>();
        for (AggregateResult result : results) {
            byId.put(result.sensorId(), result);
        }
        return new AggregateSnapshot(byId);
    }
}
