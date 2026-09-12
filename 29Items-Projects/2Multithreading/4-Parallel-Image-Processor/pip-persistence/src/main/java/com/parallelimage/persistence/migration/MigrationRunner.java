package com.parallelimage.persistence.migration;

import com.parallelimage.persistence.PersistenceException;
import com.parallelimage.persistence.jdbc.Database;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies the {@code V###__*.sql} files on the classpath, forward only, once each.
 *
 * <h2>The three rules</h2>
 * <ol>
 *   <li><b>Forward only.</b> There are no down migrations. A down migration is code that must work
 *       against a database state nobody has tested and that, for the destructive half of the cases,
 *       cannot work at all — you cannot un-drop a column's data. The recovery path for a bad
 *       migration is a new migration, and for a corrupt database it is the backup.</li>
 *   <li><b>Checksummed.</b> The SHA-256 of each applied file is stored. If a file changes after it
 *       has been applied, the runner refuses to start. This catches the single most common way a
 *       schema drifts: someone edits V001 to add a column, it works on their machine because their
 *       database was created after the edit, and it fails for everyone whose database predates it.
 *       The error names the file and says what to do instead.</li>
 *   <li><b>One transaction per migration.</b> The DDL and the {@code schema_version} row are
 *       committed together, so "recorded as applied" and "actually ran" can never disagree. SQLite
 *       supports transactional DDL, which not every database does; this is one of the places it
 *       earns its keep.</li>
 * </ol>
 *
 * <h2>Line endings</h2>
 * The checksum is taken over the file with CRLF normalised to LF and any BOM removed, <em>not</em>
 * over the raw bytes. On a repository cloned with {@code core.autocrlf=true} the raw bytes of the same
 * committed file differ between a Windows and a Linux checkout, so raw-byte checksums would report a
 * modified migration to anyone who moved a database between the two. {@code .gitattributes} pins
 * {@code *.sql} to LF as well; this is the belt to that pair of braces.
 *
 * <h2>Not thread-safe, and not meant to be</h2>
 * Migration happens once, on the thread that builds the repository, before anything else can use the
 * database. Two processes racing on the same file is handled by SQLite: the loser blocks on the write
 * lock for {@code busy_timeout}, then finds the migration already applied and does nothing.
 */
public final class MigrationRunner {

    private static final System.Logger LOG = System.getLogger(MigrationRunner.class.getName());

    /** Classpath directory the POM copies {@code /migrations/*.sql} into. */
    public static final String DEFAULT_LOCATION = "db/migration";

    /** Manifest naming the migrations in application order. See the file for why it is needed. */
    private static final String INDEX_FILE = "index.txt";

    private static final Pattern VERSIONED_NAME = Pattern.compile("^V(\\d+)__(.+)\\.sql$");

    private final Database database;
    private final String location;

    public MigrationRunner(Database database) {
        this(database, DEFAULT_LOCATION);
    }

    /**
     * Creates a runner that reads its migrations from a non-default classpath location.
     *
     * @param database the target database
     * @param location classpath directory holding {@code index.txt} and the {@code .sql} files, with
     *     no trailing slash. Overridable so a test can point at a fixture set of migrations.
     */
    public MigrationRunner(Database database, String location) {
        this.database = Objects.requireNonNull(database, "database");
        this.location = Objects.requireNonNull(location, "location");
    }

    /**
     * Brings the database up to the latest version, applying whatever is missing.
     *
     * <p>Idempotent: running it against an up-to-date database is a couple of SELECTs.
     *
     * @return what was found and what was done, for the log line the caller writes
     * @throws PersistenceException if a migration fails, if an applied file has changed, or if the
     *     database is ahead of the code
     */
    public MigrationResult run() {
        List<Migration> available = loadFromClasspath();
        try (Connection connection = database.open()) {
            createVersionTable(connection);
            Map<Integer, Applied> applied = readApplied(connection);
            verifyNoDrift(available, applied);

            int versionBefore = applied.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
            List<String> justApplied = new ArrayList<>();

            for (Migration migration : available) {
                if (applied.containsKey(migration.version())) {
                    continue;
                }
                apply(connection, migration);
                justApplied.add(migration.filename());
            }

            int versionAfter = available.isEmpty()
                    ? versionBefore
                    : Math.max(versionBefore, available.get(available.size() - 1).version());
            MigrationResult result = new MigrationResult(versionBefore, versionAfter, justApplied);
            if (justApplied.isEmpty()) {
                LOG.log(System.Logger.Level.DEBUG,
                        () -> "schema already at version " + versionAfter);
            } else {
                LOG.log(System.Logger.Level.INFO, () -> "schema migrated " + versionBefore + " -> "
                        + versionAfter + " (" + String.join(", ", justApplied) + ")");
            }
            return result;
        } catch (SQLException e) {
            throw new PersistenceException("migration failed on " + database.file(), e);
        }
    }

    /** The highest applied version, or 0 for a database that has never been migrated. */
    public int currentVersion() {
        try (Connection connection = database.open()) {
            createVersionTable(connection);
            return readApplied(connection).keySet().stream()
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElse(0);
        } catch (SQLException e) {
            throw new PersistenceException("cannot read schema version of " + database.file(), e);
        }
    }

    // ------------------------------------------------------------------------
    //  Loading
    // ------------------------------------------------------------------------

    private List<Migration> loadFromClasspath() {
        List<String> filenames = readIndex();
        List<Migration> migrations = new ArrayList<>(filenames.size());
        int previousVersion = 0;

        for (String filename : filenames) {
            Matcher matcher = VERSIONED_NAME.matcher(filename);
            if (!matcher.matches()) {
                throw new PersistenceException("migration '" + filename
                        + "' does not follow the V<number>__<description>.sql convention");
            }
            int version = Integer.parseInt(matcher.group(1));
            if (version <= previousVersion) {
                // Not pedantry: the runner applies in manifest order but records the version number,
                // so a manifest that disagrees with the numbering would record a version whose
                // migrations had not all run.
                throw new PersistenceException("migration versions must increase in " + INDEX_FILE
                        + "; found V" + version + " after V" + previousVersion);
            }
            previousVersion = version;
            String sql = readResource(filename);
            migrations.add(new Migration(version, filename, sql, sha256(sql)));
        }
        if (migrations.isEmpty()) {
            throw new PersistenceException(location + "/" + INDEX_FILE + " lists no migrations");
        }
        return migrations;
    }

    private List<String> readIndex() {
        String resource = location + "/" + INDEX_FILE;
        List<String> filenames = new ArrayList<>();
        try (InputStream in = openResource(resource);
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    filenames.add(trimmed);
                }
            }
        } catch (IOException e) {
            throw new PersistenceException("cannot read " + resource, e);
        }
        return filenames;
    }

    private String readResource(String filename) {
        String resource = location + "/" + filename;
        try (InputStream in = openResource(resource)) {
            return normalise(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new PersistenceException("cannot read " + resource, e);
        }
    }

    private InputStream openResource(String resource) {
        // The context class loader is deliberately not consulted: these resources ship in this
        // module's own jar, and asking the context loader is how you end up loading a different
        // module's migrations under an application server.
        InputStream in = MigrationRunner.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new PersistenceException("migration resource not found on classpath: " + resource
                    + " (listed in " + INDEX_FILE + " but not present; is /migrations still being"
                    + " copied by pip-persistence/pom.xml?)");
        }
        return in;
    }

    /** Strips a UTF-8 BOM and normalises line endings, so the checksum is checkout-independent. */
    private static String normalise(String raw) {
        String text = raw.startsWith("﻿") ? raw.substring(1) : raw;
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String sha256(String text) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the platform specification; this cannot happen.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // ------------------------------------------------------------------------
    //  Applying
    // ------------------------------------------------------------------------

    private void createVersionTable(Connection connection) throws SQLException {
        // Repeated in V001 for readability of the schema as plain SQL; needed here because the runner
        // must read its own history before any migration has run.
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version      INTEGER PRIMARY KEY,
                        filename     TEXT    NOT NULL UNIQUE,
                        checksum     TEXT    NOT NULL,
                        applied_at   INTEGER NOT NULL,
                        duration_ms  INTEGER NOT NULL DEFAULT 0
                    ) STRICT""");
        }
    }

    private Map<Integer, Applied> readApplied(Connection connection) throws SQLException {
        Map<Integer, Applied> applied = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT version, filename, checksum FROM schema_version ORDER BY version")) {
            while (rs.next()) {
                applied.put(rs.getInt("version"),
                        new Applied(rs.getInt("version"), rs.getString("filename"),
                                rs.getString("checksum")));
            }
        }
        return applied;
    }

    private void verifyNoDrift(List<Migration> available, Map<Integer, Applied> applied) {
        Map<Integer, Migration> byVersion = new LinkedHashMap<>();
        for (Migration migration : available) {
            byVersion.put(migration.version(), migration);
        }

        for (Applied row : applied.values()) {
            Migration migration = byVersion.get(row.version());
            if (migration == null) {
                // The database has a migration this build does not know about: an older jar against a
                // newer database. Starting anyway would mean running code against columns it does not
                // know exist, or worse, does not know are now NOT NULL.
                throw new PersistenceException("database " + database.file() + " is at schema version "
                        + row.version() + " (" + row.filename()
                        + ") which this build does not contain; upgrade the application");
            }
            if (!migration.checksum().equals(row.checksum())) {
                throw new PersistenceException(migration.filename()
                        + " has changed since it was applied to " + database.file()
                        + " (expected checksum " + row.checksum() + ", found " + migration.checksum()
                        + "). Applied migrations are immutable: revert the edit and add a new "
                        // Zero-padded, because the suggestion is meant to be usable as a filename and
                        // V4__ would sort before V10__ in the manifest that has to stay ordered.
                        + String.format("V%03d__", highestVersion(available) + 1) + " file instead.");
            }
        }
    }

    private static int highestVersion(List<Migration> available) {
        return available.isEmpty() ? 0 : available.get(available.size() - 1).version();
    }

    private void apply(Connection connection, Migration migration) throws SQLException {
        List<String> statements = SqlScript.split(migration.sql());
        long startedAt = System.currentTimeMillis();
        long startedNanos = System.nanoTime();

        connection.setAutoCommit(false);
        try {
            try (Statement statement = connection.createStatement()) {
                for (String sql : statements) {
                    statement.execute(sql);
                }
            }
            long durationMs = (System.nanoTime() - startedNanos) / 1_000_000L;
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO schema_version (version, filename, checksum, applied_at,"
                            + " duration_ms) VALUES (?, ?, ?, ?, ?)")) {
                insert.setInt(1, migration.version());
                insert.setString(2, migration.filename());
                insert.setString(3, migration.checksum());
                insert.setLong(4, startedAt);
                insert.setLong(5, durationMs);
                insert.executeUpdate();
            }
            connection.commit();
            LOG.log(System.Logger.Level.INFO,
                    () -> "applied " + migration.filename() + " (" + statements.size()
                            + " statements, " + durationMs + " ms)");
        } catch (SQLException e) {
            Database.rollbackQuietly(connection);
            throw new SQLException("migration " + migration.filename() + " failed and was rolled"
                    + " back; the database is still at the previous version", e);
        } finally {
            connection.setAutoCommit(true);
        }
    }

    // ------------------------------------------------------------------------
    //  Value types
    // ------------------------------------------------------------------------

    /** A migration file loaded from the classpath, with its normalised text and checksum. */
    private record Migration(int version, String filename, String sql, String checksum) {}

    /** A {@code schema_version} row. */
    private record Applied(int version, String filename, String checksum) {}

    /**
     * Outcome of a {@link MigrationRunner#run()}.
     *
     * @param versionBefore highest version already applied when the run started, 0 for a new database
     * @param versionAfter highest version applied when it finished
     * @param applied filenames applied by this run, in order; empty when nothing was pending
     */
    public record MigrationResult(int versionBefore, int versionAfter, List<String> applied) {

        public MigrationResult(int versionBefore, int versionAfter, List<String> applied) {
            this.versionBefore = versionBefore;
            this.versionAfter = versionAfter;
            this.applied = List.copyOf(applied);
        }

        /** True when the database was already up to date. */
        public boolean upToDate() {
            return applied.isEmpty();
        }
    }
}
