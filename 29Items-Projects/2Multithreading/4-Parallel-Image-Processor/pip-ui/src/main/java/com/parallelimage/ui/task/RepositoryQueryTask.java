package com.parallelimage.ui.task;

import com.parallelimage.core.port.JobRepository;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import javafx.concurrent.Task;

/**
 * Runs one {@link JobRepository} read off the JavaFX application thread and hands the result back on
 * it.
 *
 * <h2>Why this exists at all</h2>
 * {@code repository.recentJobs(200)} looks like a field access and behaves like a disk seek. On the
 * happy path it takes a millisecond and calling it directly from an event handler would never be
 * noticed. The problem is the unhappy path: the adapter opens SQLite with {@code busy_timeout=5000}, so
 * a read contending with the writer thread can block for <em>five seconds</em>. Five seconds on the FX
 * thread is five seconds of a frozen window with a spinning cursor, and it happens exactly when a
 * batch is running — which is when the user is most likely to click "History".
 *
 * <p>So every repository read goes through a {@link Task}. The cost is a few lines per call site; the
 * benefit is that no possible database state can freeze the window.
 *
 * <h2>Why a shared single-thread executor</h2>
 * {@code Task} does not run itself — something has to. The obvious {@code new Thread(task).start()} per
 * query is wasteful and, worse, unordered: two overlapping queries can complete in either order and the
 * older one's result can land last, so the table shows stale rows after a refresh. A single-threaded
 * executor serialises the reads, which both bounds the thread count and makes the "last query wins"
 * property automatic.
 *
 * <p>The thread is a daemon. A UI query in flight must never be the reason the JVM refuses to exit
 * after the last window closes — and since a read is idempotent and its result is discarded on
 * shutdown, there is nothing here worth waiting for.
 *
 * <h2>Failure is a result, not an exception</h2>
 * {@link JobRepository} promises never to throw into the engine, but it makes no such promise to the
 * UI, and a corrupt database file is a real thing. {@code Task} already models this: an exception in
 * {@link #call()} fires {@code onFailed} with {@link #getException()}. The view uses that to reach its
 * ERROR state and show a message, rather than silently displaying an empty table that looks like "no
 * history yet".
 */
public final class RepositoryQueryTask<T> extends Task<T> {

    private static final Logger LOG = System.getLogger(RepositoryQueryTask.class.getName());

    private static final AtomicLong THREAD_SEQUENCE = new AtomicLong();

    /**
     * The one thread all repository reads run on. See the class javadoc: single-threaded for ordering,
     * daemon so it cannot delay JVM exit.
     */
    private static final Executor QUERY_EXECUTOR = Executors.newSingleThreadExecutor(namedDaemons());

    private static ThreadFactory namedDaemons() {
        return runnable -> {
            Thread thread = new Thread(runnable, "pip-ui-query-" + THREAD_SEQUENCE.incrementAndGet());
            thread.setDaemon(true);
            // Below normal: a history refresh must never compete with the fork/join workers that are
            // doing the actual work the user is waiting for.
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        };
    }

    private final JobRepository repository;
    private final Function<JobRepository, T> query;
    private final String description;

    private RepositoryQueryTask(JobRepository repository, Function<JobRepository, T> query, String description) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.query = Objects.requireNonNull(query, "query");
        this.description = Objects.requireNonNull(description, "description");
    }

    @Override
    protected T call() {
        long startNanos = System.nanoTime();
        T result = query.apply(repository);
        long millis = (System.nanoTime() - startNanos) / 1_000_000L;
        // DEBUG, not INFO: this fires on every refresh, and a log line per keystroke-triggered query is
        // how a log file becomes useless. It is here because "the history tab feels slow" is otherwise
        // unanswerable.
        LOG.log(Level.DEBUG, () -> description + " took " + millis + " ms");
        return result;
    }

    /**
     * Submits {@code query} to the shared reader thread.
     *
     * <p>Callers attach {@code setOnSucceeded}/{@code setOnFailed} to the returned task <em>before</em>
     * it completes. That is safe even though the task may already be running: {@code Task} guarantees
     * the handlers are invoked on the FX thread after the state transition, and the transition itself is
     * posted to the FX thread — which is the same thread the caller is on, so it cannot be processed
     * mid-registration.
     *
     * @param description used only in log messages; make it read like "recent jobs (200)"
     */
    public static <T> RepositoryQueryTask<T> submit(
            JobRepository repository, String description, Function<JobRepository, T> query) {
        RepositoryQueryTask<T> task = new RepositoryQueryTask<>(repository, query, description);
        QUERY_EXECUTOR.execute(task);
        return task;
    }

    // ------------------------------------------------------------------------
    //  Named queries
    //
    //  Thin wrappers, but they keep the lambda and its description together at one
    //  site instead of letting every view invent its own wording for the same read.
    // ------------------------------------------------------------------------

    /** Most recent jobs, newest first. */
    public static RepositoryQueryTask<List<JobRepository.JobRecord>> recentJobs(
            JobRepository repository, int limit) {
        return submit(repository, "recent jobs (" + limit + ")", r -> r.recentJobs(limit));
    }

    /** Most recent batches, newest first. */
    public static RepositoryQueryTask<List<JobRepository.BatchSummary>> recentBatches(
            JobRepository repository, int limit) {
        return submit(repository, "recent batches (" + limit + ")", r -> r.recentBatches(limit));
    }

    /**
     * Human-readable reason this task failed, for a status label.
     *
     * <p>{@code getException().getMessage()} alone is not enough: a {@link NullPointerException} from a
     * bad adapter has a null message, and a status bar reading "null" tells the user nothing. Falling
     * back to the simple class name at least names the fault.
     */
    public String failureMessage() {
        Throwable cause = getException();
        if (cause == null) {
            return "";
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
