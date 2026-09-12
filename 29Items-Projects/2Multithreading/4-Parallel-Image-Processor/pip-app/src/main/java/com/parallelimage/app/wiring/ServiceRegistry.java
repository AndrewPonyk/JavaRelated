package com.parallelimage.app.wiring;

import com.parallelimage.app.config.AppConfig;
import com.parallelimage.core.engine.ImageProcessingEngine;
import com.parallelimage.core.model.JobOutcome;
import com.parallelimage.core.model.JobStatus;
import com.parallelimage.core.model.ProcessingOptions;
import com.parallelimage.core.port.JobRepository;
import com.parallelimage.core.spi.ImageEnhancer;
import com.parallelimage.persistence.jdbc.Database;
import com.parallelimage.persistence.migration.MigrationRunner;
import com.parallelimage.persistence.repository.SqliteJobRepository;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Builds the object graph. The only class that knows every module exists.
 *
 * <h2>Why the wiring is one class and not a framework</h2>
 * The graph is six objects deep and acyclic: config, database, migration runner, repository, enhancer,
 * engine. A dependency-injection container would add a scanning phase, a lifecycle model, and a class of
 * failure ("no qualifying bean") that only appears at runtime — to save roughly forty lines of
 * constructor calls that a reader can follow top to bottom. This class is that forty lines.
 *
 * <p>The pay-off is {@link #open(AppConfig)}: every layer above it — CLI, UI, HTTP — receives a fully
 * constructed {@link ImageProcessingEngine} and knows nothing about SQLite. That is the hexagonal
 * boundary in {@code pip-core/pom.xml}'s empty {@code <dependencies>} block made concrete.
 *
 * <h2>Degrading instead of refusing</h2>
 * A repository that cannot be opened does not stop the application. Processing images is the product;
 * recording that they were processed is a convenience. The two failure modes are not equivalent:
 * <ul>
 *   <li><b>No database</b> — a read-only directory, a locked file, a corrupt schema. The registry logs a
 *       warning and substitutes {@link JobRepository#NO_OP}. Batches run; history is empty.</li>
 *   <li><b>No enhancer</b> — the OpenCV JNI library is absent, which is the <em>normal</em> case on a
 *       fresh checkout. {@link ImageEnhancer#discover()} already falls back to
 *       {@code PassthroughEnhancer}, so there is nothing to handle here; the registry only makes sure the
 *       choice is reported, via {@link Registry#enhancerDescription()}, all the way to the status bar.</li>
 * </ul>
 * Silent degradation is the thing being avoided in both cases. An empty history pane with no explanation
 * produces a bug report about the repository; a logged warning at startup produces one about permissions.
 *
 * <h2>Ownership and close order</h2>
 * {@link Registry} is {@link AutoCloseable} and closes in reverse construction order: engine first, so
 * its pool is drained, then the repository, so its write queue is flushed. The order is not cosmetic —
 * the engine's workers are the repository's callers, and flushing a queue that is still being appended to
 * either loses writes or never finishes.
 *
 * <p>{@link Database} is absent from that list because it holds nothing: it is an immutable connection
 * <em>factory</em>, and the connections it hands out are owned and closed by the repository. There is no
 * pool to shut down, which is why it needs no {@code close()} and is not retained here.
 */
public final class ServiceRegistry {

    private static final Logger LOG = System.getLogger(ServiceRegistry.class.getName());

    private ServiceRegistry() {
    }

    /**
     * Everything the application needs, and the one method that takes it all down again.
     *
     * <p>A record rather than a class with getters because it is a value: constructed once, never
     * mutated, and every field is something a caller legitimately needs. The {@code close()} override is
     * the exception to "records are dumb data", and it is here rather than in a wrapper because splitting
     * ownership from the reference is how a resource ends up leaked.
     */
    public record Registry(
            AppConfig config,
            ImageProcessingEngine engine,
            JobRepository repository,
            String enhancerDescription) implements AutoCloseable {

        /** Whether history is being recorded. Reported in the UI and by {@code --version}. */
        public boolean historyEnabled() {
            return repository != JobRepository.NO_OP;
        }

        /** The options a fresh batch starts from. Delegates so callers need not hold the config too. */
        public ProcessingOptions defaultOptions() {
            return config.defaultOptions();
        }

        /**
         * Closes the engine, then the repository — reverse construction order.
         *
         * <p>Every step is individually guarded. A {@code close()} that throws half way through leaves
         * the remaining resources open, and during JVM shutdown there is nobody left to notice: the
         * shutdown hook simply stops. Each failure is logged and the next step runs anyway.
         */
        @Override
        public void close() {
            closeQuietly("engine", engine::close);
            closeQuietly("repository", repository::close);
        }

        private static void closeQuietly(String what, Runnable action) {
            try {
                action.run();
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, () -> "closing " + what + " failed: " + e);
            }
        }
    }

    /**
     * Opens every resource and wires the graph.
     *
     * @param config resolved configuration; the caller has already applied precedence
     * @return a registry the caller must close, normally from a shutdown hook
     */
    public static Registry open(AppConfig config) {
        Optional<Database> database = openDatabase(config);
        JobRepository repository = database
                .map(db -> openRepository(db, config))
                .orElse(JobRepository.NO_OP);
        purgeIfConfigured(repository, config);
        resumeCrashedJobs(repository);

        ImageEnhancer enhancer = ImageEnhancer.discover();

        ImageProcessingEngine engine = ImageProcessingEngine.builder()
                .parallelism(config.parallelism())
                .repository(repository)
                .enhancer(enhancer)
                .build();

        LOG.log(Level.INFO, () -> "engine ready: parallelism=" + engine.parallelism()
                + ", history=" + (repository == JobRepository.NO_OP ? "off" : "on")
                + ", enhancer=" + enhancer.describe());

        return new Registry(config, engine, repository, enhancer.describe());
    }

    /**
     * Opens the SQLite file and brings its schema up to date, or returns empty.
     *
     * <p>Migration runs here, not lazily on first use. A schema change that fails should fail at startup
     * with the operator watching, not forty minutes into a batch when the first row is written.
     */
    private static Optional<Database> openDatabase(AppConfig config) {
        Optional<Path> path = config.databasePath();
        if (path.isEmpty()) {
            LOG.log(Level.INFO, () -> "history disabled by configuration (db.enabled=false)");
            return Optional.empty();
        }

        Path file = path.get();
        try {
            Database database = Database.at(file);
            MigrationRunner.MigrationResult result = new MigrationRunner(database).run();
            if (result.upToDate()) {
                LOG.log(Level.DEBUG, () -> "schema at version " + result.versionAfter() + " (no change)");
            } else {
                LOG.log(Level.INFO, () -> "schema " + result.versionBefore() + " -> " + result.versionAfter()
                        + ": applied " + result.applied());
            }
            return Optional.of(database);
        } catch (RuntimeException e) {
            // Broad on purpose. The failure modes here are a locked file, a read-only directory, a
            // missing sqlite-jdbc native, and a corrupt schema -- four unrelated exception hierarchies
            // with one correct response, which is to carry on without history.
            LOG.log(Level.WARNING, () -> "cannot open " + file + " (" + e + "); running without history");
            return Optional.empty();
        }
    }

    /**
     * Deletes finished batches older than {@link AppConfig#retentionDays()}, once, at startup.
     *
     * <p>A no-op repository has nothing to purge, and {@code retentionDays() == 0} means retention is
     * switched off — both are the common case and both skip the query entirely rather than running a
     * {@code DELETE} that would affect zero rows.
     */
    private static void purgeIfConfigured(JobRepository repository, AppConfig config) {
        int days = config.retentionDays();
        if (repository == JobRepository.NO_OP || days <= 0) {
            return;
        }
        try {
            int deleted = repository.purgeOlderThan(Instant.now().minus(Duration.ofDays(days)));
            if (deleted > 0) {
                LOG.log(Level.INFO, () -> "retention purge: deleted " + deleted
                        + " batch(es) older than " + days + " day(s)");
            }
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, () -> "retention purge failed (" + e + "); continuing with existing history");
        }
    }

    /**
     * Transitions every job left {@code RUNNING} by a previous, crashed run to {@code FAILED}.
     *
     * <p>Nothing but a crash produces a {@code RUNNING} row that is still {@code RUNNING} by the
     * next startup: a normal run always reaches {@link JobRepository#recordOutcome} for a job it
     * marked running, one way or another. {@code RUNNING -> FAILED} is a legal transition (see
     * {@link JobStatus#canTransitionTo}), and going through {@link JobRepository#recordOutcome}
     * rather than a bespoke status write keeps this on the one path that already satisfies the
     * schema's {@code failure_reason IS NOT NULL} constraint for a {@code FAILED} row.
     */
    private static void resumeCrashedJobs(JobRepository repository) {
        if (repository == JobRepository.NO_OP) {
            return;
        }
        try {
            List<JobRepository.JobRecord> crashed = repository.findByStatus(JobStatus.RUNNING);
            for (JobRepository.JobRecord job : crashed) {
                repository.recordOutcome(new JobOutcome.Failure(job.jobId(),
                        "application restarted while this job was still RUNNING",
                        "com.parallelimage.app.wiring.ServiceRegistry.CrashRecovery", null, 0L));
            }
            if (!crashed.isEmpty()) {
                LOG.log(Level.INFO, () -> "crash recovery: " + crashed.size()
                        + " job(s) stuck RUNNING from a previous run marked FAILED");
            }
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING,
                    () -> "crash-recovery scan failed (" + e + "); continuing with existing history");
        }
    }

    /**
     * Wraps the database in a repository.
     *
     * <p>The {@code IntSupplier} is read on every batch insert rather than captured as an int, so a
     * {@code parallelism} recorded in history is the value that was actually in effect — which matters
     * when comparing two runs of the same batch on the same machine.
     */
    private static JobRepository openRepository(Database database, AppConfig config) {
        try {
            return SqliteJobRepository.open(database, config::parallelism);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, () -> "cannot open repository (" + e + "); running without history");
            return JobRepository.NO_OP;
        }
    }
}
