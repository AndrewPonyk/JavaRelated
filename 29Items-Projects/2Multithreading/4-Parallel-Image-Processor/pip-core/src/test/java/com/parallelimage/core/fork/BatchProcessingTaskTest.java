package com.parallelimage.core.fork;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.core.model.BatchResult;
import com.parallelimage.core.model.ImageJob;
import com.parallelimage.core.model.JobOutcome;
import com.parallelimage.core.model.ProcessingOptions;
import com.parallelimage.core.progress.ProgressEvent;
import com.parallelimage.core.progress.ProgressListener;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Level-1 decomposition tests.
 *
 * <p>Every test here uses a lambda {@link JobProcessor} and touches no disk. That is the payoff of
 * keeping I/O out of {@link BatchProcessingTask}: the splitting and merging logic — the part that is
 * genuinely hard to get right — is testable in microseconds and deterministically.
 */
class BatchProcessingTaskTest {

    private ForkJoinPool pool;

    @BeforeEach
    void createPool() {
        pool = ForkJoinConfig.newPool(4);
    }

    @AfterEach
    void shutdownPool() {
        ForkJoinConfig.shutdownGracefully(pool, 5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("an empty batch is the identity result, not a crash")
    void emptyBatchYieldsEmptyResult() {
        BatchResult result = pool.invoke(BatchProcessingTask.of(
                List.of(), 4, job -> success(job), CancellationToken.NONE, ProgressListener.NO_OP));
        assertSame(BatchResult.EMPTY, result);
    }

    @ParameterizedTest(name = "{0} jobs are all processed exactly once")
    @ValueSource(ints = {1, 2, 7, 8, 9, 64, 257})
    @DisplayName("every job runs exactly once regardless of how the tree happens to split")
    void everyJobRunsExactlyOnce(int jobCount) {
        List<ImageJob> jobs = jobs(jobCount);
        ConcurrentLinkedQueue<String> processed = new ConcurrentLinkedQueue<>();

        BatchResult result = pool.invoke(BatchProcessingTask.of(jobs, 4, job -> {
            processed.add(job.id());
            return success(job);
        }, CancellationToken.NONE, ProgressListener.NO_OP));

        assertEquals(jobCount, result.succeeded());
        assertEquals(jobCount, result.total());
        assertEquals(jobCount, processed.size(), "no job may run twice");
        assertEquals(jobCount, Set.copyOf(processed).size(), "no job may be skipped");
        assertEquals(jobCount, result.outcomes().size());
    }

    @Test
    @DisplayName("a threshold larger than the batch collapses the tree to a single leaf")
    void largeThresholdMeansNoSplitting() {
        BatchProcessingTask root = BatchProcessingTask.of(
                jobs(10), 1_000, this::successFor, CancellationToken.NONE, ProgressListener.NO_OP);
        assertEquals(10, root.rangeSize());
        assertEquals(10, pool.invoke(root).succeeded());
    }

    @Test
    @DisplayName("one bad file costs one row, not the batch")
    void failuresAreIsolatedAsData() {
        List<ImageJob> jobs = jobs(20);
        BatchResult result = pool.invoke(BatchProcessingTask.of(jobs, 2, job -> {
            // Exact match, not contains("7"): image-17.png also contains a 7, and a batch of 20 would
            // then fail twice and make the assertion below a coin toss.
            if ("image-7.png".equals(job.source().getFileName().toString())) {
                throw new IllegalStateException("simulated corrupt file");
            }
            return success(job);
        }, CancellationToken.NONE, ProgressListener.NO_OP));

        assertEquals(19, result.succeeded());
        assertEquals(1, result.failed());
        assertTrue(result.hasFailures());
        assertEquals(1, result.toExitCode());
        JobOutcome.Failure failure = result.failures().get(0);
        // JobOutcome.Failure.from records getName(), not getSimpleName(): two frameworks' worth of
        // IllegalStateException look identical in a log otherwise.
        assertEquals("java.lang.IllegalStateException", failure.exceptionType());
        assertTrue(failure.reason().contains("simulated corrupt file"));
    }

    @Test
    @DisplayName("a processor that returns Failure directly is counted, not re-wrapped")
    void processorReturnedFailuresAreCounted() {
        BatchResult result = pool.invoke(BatchProcessingTask.of(jobs(4), 1,
                job -> new JobOutcome.Failure(job.id(), "unreadable", "ImageIoException", "decode", 1L),
                CancellationToken.NONE, ProgressListener.NO_OP));
        assertEquals(0, result.succeeded());
        assertEquals(4, result.failed());
    }

    @Test
    @DisplayName("cancelling mid-batch stops starting new work and reports the jobs it skipped")
    void cancellationStopsFurtherWork() {
        CancellationToken token = new CancellationToken();
        AtomicInteger started = new AtomicInteger();

        BatchResult result = pool.invoke(BatchProcessingTask.of(jobs(200), 1, job -> {
            if (started.incrementAndGet() == 10) {
                token.cancel();
            }
            return success(job);
        }, token, ProgressListener.NO_OP));

        assertEquals(200, result.total(), "every job must still be accounted for");
        assertTrue(result.cancelled() > 0, "cancellation must be visible in the result");
        assertTrue(result.succeeded() < 200, "work must actually have stopped");
        // Jobs that were never started must not have run their processor.
        assertTrue(started.get() < 200, "processor kept being invoked after cancellation");
    }

    @Test
    @DisplayName("progress events are monotonic and end at the batch size")
    void progressCountsAreMonotonic() {
        int jobCount = 50;
        ConcurrentLinkedQueue<ProgressEvent> events = new ConcurrentLinkedQueue<>();

        pool.invoke(BatchProcessingTask.of(jobs(jobCount), 4, this::successFor,
                CancellationToken.NONE, events::add));

        List<Integer> completions = new ArrayList<>();
        for (ProgressEvent event : events) {
            if (event.phase() == ProgressEvent.Phase.JOB_COMPLETED) {
                completions.add(event.completed());
            }
        }
        assertEquals(jobCount, completions.size());
        assertEquals(jobCount, completions.stream().mapToInt(Integer::intValue).max().orElseThrow(),
                "the last completion must report the full count");
        assertEquals(jobCount, Set.copyOf(completions).size(),
                "the shared counter must hand out each value once — a per-subtree counter would not");
    }

    @Test
    @DisplayName("merge is associative and commutative, so join order cannot change the result")
    void mergeIsOrderIndependent() {
        BatchResult a = BatchResult.of(new JobOutcome.Success("a", Path.of("a.png"), 10L, 100L));
        BatchResult b = BatchResult.of(new JobOutcome.Failure("b", "why", "Ex", "blur", 20L));
        BatchResult c = BatchResult.of(new JobOutcome.Cancelled("c", 30L));

        BatchResult leftLeaning = a.merge(b).merge(c);
        BatchResult rightLeaning = a.merge(b.merge(c));
        BatchResult reversed = c.merge(b).merge(a);

        for (BatchResult result : List.of(leftLeaning, rightLeaning, reversed)) {
            assertEquals(1, result.succeeded());
            assertEquals(1, result.failed());
            assertEquals(1, result.cancelled());
            assertEquals(60L, result.totalNanos());
            assertEquals(100L, result.pixelsProcessed());
        }
        assertFalse(BatchResult.EMPTY.merge(a).isEmpty());
        assertSame(a, a.merge(BatchResult.EMPTY), "identity merge must not copy the outcome list");
    }

    private JobOutcome successFor(ImageJob job) {
        return success(job);
    }

    private static JobOutcome success(ImageJob job) {
        return new JobOutcome.Success(job.id(), job.target(), 1_000L, 1_024L);
    }

    private static List<ImageJob> jobs(int count) {
        ProcessingOptions options = ProcessingOptions.defaults();
        List<ImageJob> jobs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            jobs.add(ImageJob.create("batch-1",
                    Path.of("in", "image-" + i + ".png"),
                    Path.of("out", "image-" + i + ".png"),
                    options));
        }
        return jobs;
    }
}
