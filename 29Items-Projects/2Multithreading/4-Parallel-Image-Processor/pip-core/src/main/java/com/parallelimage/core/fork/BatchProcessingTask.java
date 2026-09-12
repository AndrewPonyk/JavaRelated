package com.parallelimage.core.fork;

import com.parallelimage.core.model.BatchResult;
import com.parallelimage.core.model.ImageJob;
import com.parallelimage.core.model.JobOutcome;
import com.parallelimage.core.progress.ProgressEvent;
import com.parallelimage.core.progress.ProgressListener;
import com.parallelimage.core.util.Preconditions;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <strong>Level 1 of the decomposition:</strong> recursively halves a batch of {@link ImageJob}s
 * until each leaf holds few enough jobs to process sequentially, then reduces the per-job
 * {@link JobOutcome}s bottom-up into a single {@link BatchResult}.
 *
 * <h2>Why {@code RecursiveTask} and not a queue of {@code Callable}s</h2>
 * A thread pool with a shared queue is a perfectly good way to run N independent jobs — right up to
 * the moment a job wants to parallelize <em>itself</em>. Fork/join gives us that for free: the leaf
 * here calls a {@link JobProcessor} which forks a {@link TileProcessingAction} tree in the
 * <em>same</em> pool, so idle workers steal tile-level work without any coordination from us. The
 * reduction is also expressed structurally rather than with a shared accumulator, which removes the
 * only contended write a naive implementation would have.
 *
 * <h2>Index ranges, not sublists</h2>
 * The task holds {@code (jobs, lo, hi)} rather than a copied {@code List}. Splitting is then O(1)
 * and allocation-free; copying sublists at every level of a 10 000-job batch would allocate
 * ~O(n log n) list nodes for no benefit and put avoidable pressure on the collector.
 *
 * <h2>Failure containment</h2>
 * The leaf never lets an exception escape. One unreadable JPEG in a 10 000-image batch must not
 * cancel 9 999 siblings — and it would, because a {@code ForkJoinTask} that completes exceptionally
 * propagates that exception into every ancestor's {@code join()}. Errors are therefore <em>data</em>
 * ({@link JobOutcome.Failure}), never control flow. The single deliberate exception is
 * {@link CancellationException}, which <em>should</em> unwind the whole tree.
 *
 * <p><strong>Not reusable.</strong> Construct a fresh tree per batch.
 *
 * @see BatchResult for the monoid laws the merge relies on
 */
public final class BatchProcessingTask extends RecursiveTask<BatchResult> {

    private static final long serialVersionUID = 1L;

    /** Fewer jobs per leaf than this and the fork overhead dominates. See TECH-NOTES §3.6 A4. */
    public static final int MIN_USEFUL_THRESHOLD_JOBS = 1;

    private final transient List<ImageJob> jobs;
    private final int lo;
    private final int hi;
    private final int thresholdJobs;
    private final transient JobProcessor processor;
    private final transient CancellationToken token;
    private final transient ProgressListener listener;
    /** Shared across the whole tree so the UI sees a monotonically rising completed count. */
    private final transient AtomicInteger completedCounter;
    private final transient String batchId;

    private BatchProcessingTask(List<ImageJob> jobs, int lo, int hi, int thresholdJobs,
            JobProcessor processor, CancellationToken token, ProgressListener listener,
            AtomicInteger completedCounter, String batchId) {
        this.jobs = jobs;
        this.lo = lo;
        this.hi = hi;
        this.thresholdJobs = thresholdJobs;
        this.processor = processor;
        this.token = token;
        this.listener = listener;
        this.completedCounter = completedCounter;
        this.batchId = batchId;
    }

    /**
     * Creates the root of a batch tree.
     *
     * @param jobs          jobs to run; copied defensively, so later mutation by the caller is safe
     * @param thresholdJobs leaf size, clamped to at least {@link #MIN_USEFUL_THRESHOLD_JOBS}
     * @param processor     per-job worker
     * @param token         cooperative cancellation shared by the batch; {@code null} → never
     * @param listener      progress sink; {@code null} → {@link ProgressListener#NO_OP}
     */
    public static BatchProcessingTask of(List<ImageJob> jobs, int thresholdJobs,
            JobProcessor processor, CancellationToken token, ProgressListener listener) {
        List<ImageJob> snapshot = List.copyOf(Preconditions.requireNonNull(jobs, "jobs"));
        Preconditions.requireNonNull(processor, "processor");
        String id = snapshot.isEmpty() ? "empty-batch" : snapshot.get(0).batchId();
        return new BatchProcessingTask(
                snapshot,
                0,
                snapshot.size(),
                Math.max(MIN_USEFUL_THRESHOLD_JOBS, thresholdJobs),
                processor,
                token == null ? CancellationToken.NONE : token,
                listener == null ? ProgressListener.NO_OP : listener,
                new AtomicInteger(),
                id);
    }

    @Override
    protected BatchResult compute() {
        int size = hi - lo;
        if (size <= 0) {
            return BatchResult.EMPTY;
        }
        if (size <= thresholdJobs) {
            return computeSequentially();
        }

        int mid = lo + (size >>> 1);
        BatchProcessingTask left = subRange(lo, mid);
        BatchProcessingTask right = subRange(mid, hi);

        // invokeAll forks the right half, computes the left on this thread, and joins in reverse
        // order -- the ordering the work-stealing deque is built for. Doing fork()/fork()/join()
        // /join() here would idle the current thread and invert that locality (TECH-NOTES §3.6 A2).
        invokeAll(left, right);

        // Both are complete, so these joins do not block. Merge is associative, which is what makes
        // the arbitrary interleaving of sibling completions irrelevant to the result.
        return left.join().merge(right.join());
    }

    private BatchProcessingTask subRange(int fromInclusive, int toExclusive) {
        return new BatchProcessingTask(jobs, fromInclusive, toExclusive, thresholdJobs, processor,
                token, listener, completedCounter, batchId);
    }

    /** The leaf: run each job in this range on the current worker thread. */
    private BatchResult computeSequentially() {
        BatchResult accumulated = BatchResult.EMPTY;
        for (int i = lo; i < hi; i++) {
            accumulated = accumulated.merge(BatchResult.of(runOne(jobs.get(i))));
        }
        return accumulated;
    }

    /**
     * Runs one job, converting every failure mode into a {@link JobOutcome}.
     *
     * <p>Catching {@link Throwable} is normally a smell. Here it is the point: this is the boundary
     * where "a bad input file" is turned into a value. {@link Error} is rethrown, because an
     * {@code OutOfMemoryError} or {@code StackOverflowError} is not something the batch can
     * meaningfully continue past.
     */
    private JobOutcome runOne(ImageJob job) {
        long startNanos = System.nanoTime();

        // Checked before starting, not after: a cancelled batch should stop launching new decodes
        // immediately rather than draining the remaining leaves.
        if (token.isCancelled()) {
            return publish(new JobOutcome.Cancelled(job.id(), System.nanoTime() - startNanos), job);
        }

        listener.onProgress(new ProgressEvent(batchId, job.id(), ProgressEvent.Phase.JOB_STARTED,
                completedCounter.get(), jobs.size(), job.displayName()));
        try {
            return publish(processor.process(job), job);
        } catch (CancellationException e) {
            // Deliberate: unwinding the tree is the desired behaviour for cancellation.
            throw e;
        } catch (RuntimeException e) {
            return publish(JobOutcome.Failure.from(job.id(), e, null,
                    System.nanoTime() - startNanos), job);
        } catch (Error e) {
            throw e;
        } catch (Throwable e) {
            return publish(JobOutcome.Failure.from(job.id(), e, null,
                    System.nanoTime() - startNanos), job);
        }
    }

    private JobOutcome publish(JobOutcome outcome, ImageJob job) {
        int done = completedCounter.incrementAndGet();
        ProgressEvent.Phase phase = switch (outcome) {
            case JobOutcome.Success s -> ProgressEvent.Phase.JOB_COMPLETED;
            case JobOutcome.Failure f -> ProgressEvent.Phase.JOB_FAILED;
            case JobOutcome.Cancelled c -> ProgressEvent.Phase.JOB_CANCELLED;
        };
        listener.onProgress(
                new ProgressEvent(batchId, job.id(), phase, done, jobs.size(), job.displayName()));
        return outcome;
    }

    /** Exposed for tests that assert split geometry without running a pool. */
    public int rangeSize() {
        return hi - lo;
    }

    @Override
    public String toString() {
        return "BatchProcessingTask[" + lo + ".." + hi + " of " + jobs.size() + "]";
    }

    /**
     * {@code RecursiveTask} inherits {@code Serializable} from {@code ForkJoinTask}, but every
     * collaborator here is {@code transient} and this tree is never meant to cross that boundary
     * (see the class javadoc: "Not reusable. Construct a fresh tree per batch."). Left alone, the
     * default mechanism would silently serialize the {@code lo}/{@code hi}/{@code thresholdJobs}
     * ints, skip the transient fields, and hand back a task that NPEs the moment {@link #compute()}
     * touches {@code jobs} or {@code processor}. Refusing outright beats that silent corruption.
     */
    private void writeObject(ObjectOutputStream out) throws NotSerializableException {
        throw new NotSerializableException(getClass().getName());
    }

    private void readObject(ObjectInputStream in) throws NotSerializableException {
        throw new NotSerializableException(getClass().getName());
    }
}
