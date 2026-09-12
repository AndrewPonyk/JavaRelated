package com.parallelimage.persistence.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.persistence.PersistenceException;
import com.parallelimage.persistence.jdbc.Database;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link MigrationRunner} tests against a real SQLite file.
 *
 * <p><strong>Not {@code :memory:}.</strong> An in-memory database cannot use WAL, drops its content
 * when the connection closes, and so would quietly turn "does the migration persist" into a test that
 * passes for the wrong reason. {@code @TempDir} gives a real file for the cost of a few milliseconds.
 *
 * <p>These tests run the migrations from {@code /migrations} — the ones that ship — rather than from a
 * fixture set. A fixture would test the runner; this tests the runner <em>and</em> that the SQL we
 * actually ship applies cleanly to an empty database, which is the failure that would matter on a
 * user's first launch.
 */
class MigrationRunnerTest {

    @TempDir
    Path tempDir;

    private Database database;

    @BeforeEach
    void createDatabase() {
        database = Database.at(tempDir.resolve("history.db"));
    }

    @Test
    @DisplayName("a fresh database is migrated to the latest shipped version")
    void migratesFromScratch() {
        assertEquals(0, new MigrationRunner(database).currentVersion(),
                "a database that has never been migrated is at version 0");

        MigrationRunner.MigrationResult result = new MigrationRunner(database).run();

        assertEquals(0, result.versionBefore());
        assertEquals(3, result.versionAfter(), "V001..V003 ship today; bump this when V004 lands");
        assertEquals(List.of("V001__init_schema.sql", "V002__watermark_presets.sql",
                "V003__indexes_and_views.sql"), result.applied());
        assertFalse(result.upToDate());
        assertTrue(Files.exists(tempDir.resolve("history.db")), "the file should exist now");
    }

    @Test
    @DisplayName("every table and view the adapter queries exists after migrating")
    void createsTheWholeSchema() throws SQLException {
        new MigrationRunner(database).run();

        List<String> tables = objectNames("table");
        assertTrue(tables.containsAll(List.of("schema_version", "batches", "jobs", "image_metadata",
                "watermark_presets", "pipeline_presets")), "tables: " + tables);

        List<String> views = objectNames("view");
        assertTrue(views.containsAll(
                List.of("v_recent_jobs", "v_batch_summary", "v_batch_counter_drift")),
                "views: " + views);

        List<String> indexes = objectNames("index");
        assertTrue(indexes.containsAll(List.of("idx_jobs_recorded_at", "idx_jobs_batch",
                "idx_jobs_unfinished", "idx_batches_started_at", "idx_batches_finished_at")),
                "indexes: " + indexes);
    }

    @Test
    @DisplayName("the seed presets are inserted exactly once, however often the runner runs")
    void seedsArePresentAndNotDuplicated() throws SQLException {
        new MigrationRunner(database).run();
        new MigrationRunner(database).run();

        assertEquals(3, count("SELECT COUNT(*) FROM watermark_presets"));
        assertEquals(3, count("SELECT COUNT(*) FROM pipeline_presets"));
    }

    @Test
    @DisplayName("running again is a no-op, not a re-apply")
    void isIdempotent() {
        new MigrationRunner(database).run();

        MigrationRunner.MigrationResult second = new MigrationRunner(database).run();

        assertTrue(second.upToDate());
        assertEquals(3, second.versionBefore());
        assertEquals(3, second.versionAfter());
        assertEquals(List.of(), second.applied());
    }

    @Test
    @DisplayName("editing an applied migration is refused, and the message says what to do instead")
    void refusesToStartWhenAnAppliedMigrationChanged() throws SQLException {
        new MigrationRunner(database).run();
        // Equivalent to someone editing V001 after release: the file on the classpath no longer
        // hashes to what was recorded.
        execute("UPDATE schema_version SET checksum = 'tampered' WHERE version = 1");

        PersistenceException thrown =
                assertThrows(PersistenceException.class, () -> new MigrationRunner(database).run());

        assertTrue(thrown.getMessage().contains("V001__init_schema.sql"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("V004__"),
                "the message should name the next free version, got: " + thrown.getMessage());
    }

    @Test
    @DisplayName("an older build refuses to run against a newer database")
    void refusesADatabaseFromTheFuture() throws SQLException {
        new MigrationRunner(database).run();
        execute("INSERT INTO schema_version (version, filename, checksum, applied_at)"
                + " VALUES (999, 'V999__from_the_future.sql', 'x', 0)");

        PersistenceException thrown =
                assertThrows(PersistenceException.class, () -> new MigrationRunner(database).run());

        assertTrue(thrown.getMessage().contains("upgrade the application"), thrown.getMessage());
    }

    @Test
    @DisplayName("a manifest naming a file that is not there fails with a message that says so")
    void reportsAMissingResource() {
        // Pointing at a location with no index.txt at all is the same class of failure: the runner
        // must not decide the schema is simply up to date.
        MigrationRunner runner = new MigrationRunner(database, "db/no-such-location");

        PersistenceException thrown = assertThrows(PersistenceException.class, runner::run);
        assertTrue(thrown.getMessage().contains("db/no-such-location"), thrown.getMessage());
    }

    @Test
    @DisplayName("each migration records how long it took and when")
    void recordsBookkeeping() throws SQLException {
        long before = System.currentTimeMillis();
        new MigrationRunner(database).run();

        try (Connection connection = database.open();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT version, filename, checksum, applied_at, duration_ms"
                                + " FROM schema_version ORDER BY version")) {
            int rows = 0;
            while (rs.next()) {
                rows++;
                assertEquals(rows, rs.getInt("version"));
                assertTrue(rs.getString("filename").startsWith("V00"), rs.getString("filename"));
                assertEquals(64, rs.getString("checksum").length(), "SHA-256 as hex is 64 chars");
                assertTrue(rs.getLong("applied_at") >= before, "applied_at should be now-ish");
                assertTrue(rs.getLong("duration_ms") >= 0);
            }
            assertEquals(3, rows);
        }
    }

    @Test
    @DisplayName("a CRLF checkout and an LF checkout agree on the checksum")
    void checksumIgnoresLineEndings() throws IOException {
        // src/test/resources/db/fixture-lf and db/fixture-crlf hold the same migration twice: once
        // with LF terminators and no BOM, once with CRLF and a UTF-8 BOM — 239 bytes against 245.
        // That is what the same file looks like in a clone made with core.autocrlf=true, and taking
        // the checksum over raw bytes would report it as a tampered migration the moment a user
        // copied ~/.pip/pip.db between a Windows and a Linux machine.
        assertFixturesStillDiffer();

        MigrationRunner.MigrationResult lf = new MigrationRunner(database, "db/fixture-lf").run();
        assertEquals(List.of("V001__fixture.sql"), lf.applied());

        assertTrue(new MigrationRunner(database, "db/fixture-crlf").run().upToDate(),
                "the CRLF twin must be recognised as already applied, not as changed");
    }

    /**
     * Fails if the two fixtures have stopped being byte-different.
     *
     * <p>This assertion is about the repository, not the runner. {@code .gitattributes} marks both
     * fixture directories {@code -text} so no end-of-line conversion is applied to them; delete those
     * two lines and {@code git add} on a machine with {@code core.autocrlf=true} rewrites one fixture
     * so that both end up with the same terminators. The pair then differs only by the BOM, the
     * CRLF half of the test above silently stops testing anything, and the test still passes — which
     * is the one outcome worth spending eight lines to prevent.
     *
     * <p>The sizes are read from the classpath rather than from {@code src/test/resources}, because
     * that is the copy the runner reads and therefore the copy whose bytes matter.
     */
    private static void assertFixturesStillDiffer() throws IOException {
        long lf = fixtureSize("db/fixture-lf/V001__fixture.sql");
        long crlf = fixtureSize("db/fixture-crlf/V001__fixture.sql");
        assertTrue(crlf > lf, () -> "the CRLF fixture (" + crlf + " bytes) must be larger than its LF "
                + "twin (" + lf + "); if they are equal, end-of-line normalisation has been applied to "
                + "src/test/resources/db/fixture-* and this test no longer proves anything — restore the "
                + "-text entries in .gitattributes");
    }

    private static long fixtureSize(String resource) throws IOException {
        try (InputStream in = MigrationRunnerTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is missing from the test classpath");
            return in.readAllBytes().length;
        }
    }

    // ------------------------------------------------------------------------

    private List<String> objectNames(String type) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Connection connection = database.open();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT name FROM sqlite_master WHERE type = '" + type + "'")) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }

    private int count(String sql) throws SQLException {
        try (Connection connection = database.open();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = database.open();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
