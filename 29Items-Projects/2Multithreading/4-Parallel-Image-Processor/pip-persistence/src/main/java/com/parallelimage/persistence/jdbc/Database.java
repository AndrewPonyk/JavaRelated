package com.parallelimage.persistence.jdbc;

import com.parallelimage.persistence.PersistenceException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Connection factory for the one SQLite file the application keeps history in.
 *
 * <h2>Why a factory and not a pool</h2>
 * SQLite is a file, not a server: opening a connection is a {@code fopen}, not a TCP handshake and a
 * round trip, so the usual reason to pool does not apply. The write path holds exactly one long-lived
 * connection on its own thread (see {@code SqliteJobRepository}) because SQLite permits one writer at
 * a time and serialising in Java is cheaper than colliding in C and retrying. Reads open a
 * short-lived connection each and close it; under WAL they do not block the writer and the writer does
 * not block them.
 *
 * <h2>Pragmas, and why they are here rather than in a migration</h2>
 * Two of these are per-connection settings that reset on every open, so a migration could not set
 * them usefully even if it wanted to:
 *
 * <ul>
 *   <li>{@code foreign_keys = ON} — SQLite defaults this <em>off</em> for backwards compatibility.
 *       The schema declares {@code ON DELETE CASCADE} on {@code jobs} and {@code image_metadata};
 *       without the pragma those declarations are decorative and the retention purge silently leaves
 *       orphans. A silently unenforced constraint is worse than no constraint.</li>
 *   <li>{@code busy_timeout = 5000} — without it a concurrent writer fails instantly with
 *       {@code SQLITE_BUSY}. With it SQLite retries internally for five seconds, which covers every
 *       contention window this application can produce (a UI read racing a batch flush) and still
 *       gives up long before a user thinks the app has hung.</li>
 * </ul>
 *
 * <p>{@code journal_mode = WAL} is different: it is persistent, stored in the file header, so setting
 * it once would be enough. It is set on every open anyway because it is idempotent and because that
 * is what makes the failure visible — see {@link #open()}.
 *
 * <p>{@code synchronous = NORMAL} is the deliberate durability trade. Under WAL it means a commit
 * does not wait for an fsync; a power cut can lose the last transactions but cannot corrupt the
 * database. This file holds a record of work whose real output is the image files on disk, so losing
 * the last few history rows to a power cut costs a line in a table, and paying an fsync per commit on
 * the hot path of a 10 000-image batch costs real throughput.
 *
 * <p>Instances are immutable and safe to share; the {@link Connection}s handed out are not.
 */
public final class Database {

    private static final System.Logger LOG = System.getLogger(Database.class.getName());

    /** Milliseconds SQLite retries a locked database internally before reporting SQLITE_BUSY. */
    private static final int BUSY_TIMEOUT_MS = 5_000;

    private final Path file;
    private final String url;

    private Database(Path file) {
        this.file = file.toAbsolutePath().normalize();
        this.url = "jdbc:sqlite:" + this.file;
    }

    /**
     * Opens the database at an explicit path, creating the parent directory if needed.
     *
     * <p>The file itself is created lazily by the first {@link #open()} — SQLite does that, and
     * pre-creating an empty file here would produce a zero-byte "database" that fails on read with a
     * far less obvious message than "unable to open database file".
     *
     * @throws PersistenceException if the parent directory cannot be created
     */
    public static Database at(Path file) {
        Objects.requireNonNull(file, "file");
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new PersistenceException("cannot create database directory " + parent, e);
            }
        }
        return new Database(file);
    }

    /**
     * The default location, {@code ~/.pip/pip.db}.
     *
     * <p>Under the home directory rather than next to the jar so that an installation in
     * {@code Program Files} still works for an unprivileged user, and so two users on one machine get
     * their own history. {@code AppConfig} can override it with {@code PIP_DB_PATH}.
     */
    public static Database atDefaultLocation() {
        return at(defaultPath());
    }

    /** The path {@link #atDefaultLocation()} would use, without touching the filesystem. */
    public static Path defaultPath() {
        return Paths.get(System.getProperty("user.home", "."), ".pip", "pip.db");
    }

    /** The resolved absolute path of the database file. Useful in log lines and error dialogs. */
    public Path file() {
        return file;
    }

    /** The JDBC URL, exposed for diagnostics rather than for callers to open themselves. */
    public String url() {
        return url;
    }

    /**
     * Opens a connection with the pragmas above applied and auto-commit left on.
     *
     * <p>Callers that need a transaction turn auto-commit off themselves, so that the scope of the
     * transaction is visible at the call site instead of being a property of how the connection was
     * obtained.
     *
     * @throws PersistenceException if the file cannot be opened; that is fatal for this adapter and
     *     the caller is expected to fall back to {@code JobRepository.NO_OP}
     */
    public Connection open() {
        Connection connection = null;
        try {
            connection = DriverManager.getConnection(url);
            applyPragmas(connection);
            return connection;
        } catch (SQLException e) {
            closeQuietly(connection);
            throw new PersistenceException("cannot open SQLite database at " + file, e);
        }
    }

    private void applyPragmas(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // Not silently ignored: journal_mode returns the mode actually in effect, and a network
            // share or a read-only directory quietly leaves it at 'delete'. That still works, just
            // without concurrent readers, so warn rather than fail — a user pointing PIP_DB_PATH at a
            // network drive should get a slow app and an explanation, not a refusal to start.
            try (ResultSet rs = statement.executeQuery("PRAGMA journal_mode = WAL")) {
                String mode = rs.next() ? rs.getString(1) : "unknown";
                if (!"wal".equalsIgnoreCase(mode)) {
                    LOG.log(System.Logger.Level.WARNING,
                            () -> "WAL unavailable for " + file + " (journal_mode=" + mode
                                    + "); history reads will block while a batch is writing");
                }
            }
            statement.execute("PRAGMA busy_timeout = " + BUSY_TIMEOUT_MS);
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA synchronous = NORMAL");
        }
    }

    /**
     * Closes a connection, swallowing the failure.
     *
     * <p>For use in {@code finally} blocks and failure paths where a close error would mask the
     * exception actually being propagated. Everywhere else, close via try-with-resources.
     */
    public static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            LOG.log(System.Logger.Level.DEBUG, "failed to close connection", e);
        }
    }

    /**
     * Rolls a connection back, swallowing the failure, and restores auto-commit.
     *
     * <p>Restoring auto-commit matters for the writer thread, which reuses one connection for the
     * lifetime of the process: leaving it off after a failed transaction would silently make the next
     * batch of writes part of a transaction nobody commits.
     */
    public static void rollbackQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            if (!connection.getAutoCommit()) {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOG.log(System.Logger.Level.DEBUG, "rollback failed", e);
        }
    }

    @Override
    public String toString() {
        return "Database[" + file + "]";
    }
}
