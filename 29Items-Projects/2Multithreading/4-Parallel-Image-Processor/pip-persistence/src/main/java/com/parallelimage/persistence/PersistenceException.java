package com.parallelimage.persistence;

/**
 * Unchecked wrapper for a {@link java.sql.SQLException} that a caller can actually act on.
 *
 * <h2>When this is thrown, and when it deliberately is not</h2>
 * {@link com.parallelimage.core.port.JobRepository} documents that adapters never throw into the
 * engine: a locked database must not fail a batch whose output files are already on disk. So the
 * <em>write</em> path ({@code saveBatch}, {@code recordOutcome}, {@code recordMetadata},
 * {@code completeBatch}) logs and swallows, and this exception never escapes it.
 *
 * <p>The <em>read</em> path is different, and the asymmetry is intentional. {@code recentJobs},
 * {@code recentBatches} and {@code findBatch} are called by the UI on a background task that has an
 * ERROR state built for precisely this case. Returning an empty list from a database that is corrupt
 * or unreadable would render as "you have no history yet", which is a lie the user cannot recover
 * from. Failing loudly there is the kinder behaviour.
 *
 * <p>Opening and migrating also throw: refusing to start beats starting against a schema the code
 * does not understand. {@code pip-app} catches that at the boundary and falls back to
 * {@link com.parallelimage.core.port.JobRepository#NO_OP} with a warning, which is what lets the
 * application run read-only from a network share.
 */
public class PersistenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
