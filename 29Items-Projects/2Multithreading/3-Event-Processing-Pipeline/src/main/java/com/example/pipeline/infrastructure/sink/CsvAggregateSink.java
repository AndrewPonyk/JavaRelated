package com.example.pipeline.infrastructure.sink;

import com.example.pipeline.application.port.AggregateSink;
import com.example.pipeline.domain.AggregateResult;
import com.example.pipeline.domain.AggregateSnapshot;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writes the aggregate table to a CSV file under a fixed output root.
 *
 * <p><strong>Path containment.</strong> The output root is resolved and normalised once,
 * and the target file must stay inside it. Without that check a configured filename of
 * {@code ../../etc/something} would write outside the intended directory — the one place
 * in this process where configuration turns into a filesystem write (see
 * {@code docs/ARCHITECTURE.md} §2.5).
 *
 * <p><strong>Deliberately simple.</strong> The snapshot has one row per sensor (tens of
 * rows), written once at the end of a run, so a single buffered write is the right
 * amount of machinery. It is not a streaming, rotating or append-safe writer, and
 * nothing here should grow into one — that would be a different component.
 *
 * <p><strong>Crash-safe by construction.</strong> A report is never written in place.
 * Every row is rendered and validated first, then written to a unique temporary file in
 * the same directory, then moved onto the target with {@link StandardCopyOption#ATOMIC_MOVE}.
 * Two failure modes disappear as a result:
 * <ul>
 *   <li>a crash, full disk or kill mid-write leaves the <em>previous</em> report intact and
 *       a stray {@code .tmp} beside it, rather than a truncated file that still parses as
 *       CSV and reads as a complete report — the worst outcome, because it is silent;</li>
 *   <li>a row that cannot be rendered (see {@link #toCsvRow}) is discovered before the
 *       target is opened, so a bad sensor id no longer destroys a good report on its way
 *       to throwing.</li>
 * </ul>
 * A reader therefore only ever observes a complete file or the last complete file. Atomic
 * move requires source and target on one filesystem, which is why the temporary file is
 * created in the target's own directory and not in {@code java.io.tmpdir}.
 */
public final class CsvAggregateSink implements AggregateSink {

    private static final Logger LOG = Logger.getLogger(CsvAggregateSink.class.getName());

    private static final String HEADER = "sensor_id,count,min_value,max_value,avg_value,sum_value";

    /** Suffix for the in-progress file; a stray one means a write was interrupted. */
    private static final String TEMP_SUFFIX = ".tmp";

    private final Path outputFile;

    /**
     * @param outputDir root directory; created if missing
     * @param fileName  file name inside {@code outputDir}; must not escape it
     * @throws IllegalArgumentException if {@code fileName} resolves outside {@code outputDir}
     */
    public CsvAggregateSink(String outputDir, String fileName) {
        Objects.requireNonNull(outputDir, "outputDir");
        Objects.requireNonNull(fileName, "fileName");
        Path root;
        try {
            root = Path.of(outputDir).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("outputDir is not a valid path: " + outputDir, e);
        }
        Path resolved = root.resolve(fileName).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("fileName must stay inside " + root + " but resolved to " + resolved);
        }
        this.outputFile = resolved;
    }

    @Override
    public void publish(AggregateSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        // Render before opening anything. toCsvRow can reject a row, and doing that while
        // the target is already truncated would trade a thrown exception for a lost report.
        List<String> rows = new ArrayList<>(snapshot.sensorCount());
        for (AggregateResult result : snapshot.sortedBySensorId()) {
            rows.add(toCsvRow(result));
        }
        try {
            Path parent = outputFile.getParent();
            Files.createDirectories(parent);
            // Unique per call, so two runs sharing an output directory cannot half-write
            // each other's temporary file.
            Path temp = Files.createTempFile(parent, outputFile.getFileName() + "-", TEMP_SUFFIX);
            try {
                try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                    writer.write(HEADER);
                    writer.newLine();
                    for (String row : rows) {
                        writer.write(row);
                        writer.newLine();
                    }
                }
                moveIntoPlace(temp);
            } catch (IOException | RuntimeException e) {
                // Leaving a stray .tmp on a failed write would accumulate one per failure.
                deleteQuietly(temp);
                throw e;
            }
            LOG.info("wrote " + rows.size() + " aggregate row(s) to " + outputFile);
        } catch (IOException e) {
            // Wrapped, not swallowed: the port's contract is a RuntimeException, and the
            // aggregation stage counts it as an error instead of failing the whole run.
            throw new UncheckedIOException("could not write " + outputFile, e);
        }
    }

    /**
     * Moves the finished temporary file onto the target.
     *
     * <p>Atomic where the filesystem supports it. {@code ATOMIC_MOVE} is not universal —
     * some network and FAT-derived filesystems refuse it — so a non-atomic replace is the
     * fallback rather than a failed run: a slightly weaker guarantee still beats losing the
     * report entirely. The downgrade is logged, because it changes the crash semantics
     * this class advertises.
     */
    private void moveIntoPlace(Path temp) throws IOException {
        try {
            Files.move(temp, outputFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            LOG.log(Level.WARNING, "filesystem does not support atomic move; replacing {0} non-atomically",
                    outputFile);
            Files.move(temp, outputFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Best-effort cleanup on a failed write; the original failure must not be masked. */
    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException suppressed) {
            LOG.log(Level.FINE, "could not remove temporary file " + path, suppressed);
        }
    }

    /** The file this sink writes to — absolute and normalised. */
    public Path outputFile() {
        return outputFile;
    }

    /**
     * Renders one row.
     *
     * <p>No quoting or escaping: sensor ids are generated as {@code sensor-NN} and the
     * remaining fields are numbers, so no field can contain a comma. If sensor ids ever
     * become externally supplied, this needs a real CSV writer — hence the assertion
     * below rather than a silent assumption.
     */
    private static String toCsvRow(AggregateResult result) {
        if (result.sensorId().indexOf(',') >= 0 || result.sensorId().indexOf('"') >= 0) {
            throw new IllegalArgumentException(
                    "sensor id needs CSV quoting, which this sink does not do: " + result.sensorId());
        }
        return String.format(Locale.ROOT, "%s,%d,%.6f,%.6f,%.6f,%.6f",
                result.sensorId(), result.count(), result.min(), result.max(), result.average(), result.sum());
    }
}
