package com.example.pipeline.infrastructure.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The four pools and the shutdown escalation that gets them all to terminate.
 *
 * <p>Two tests carry the weight. {@link #forkJoinPoolIsNotTheCommonPool()} pins the decision
 * that makes the aggregation stage parallel at all: {@code commonPool()}'s default
 * parallelism is {@code cores - 1}, which is <em>zero workers</em> on a single-core CI
 * runner, so work would silently run on the calling thread and every "parallel aggregation"
 * measurement in the report would be a lie. {@link #anUninterruptibleTaskIsReported()} pins
 * the other half — {@code shutdownNow()} only interrupts, so a task that ignores interrupts
 * is still running when it returns, and {@code shutdownAndAwait} has to answer {@code false}
 * rather than claim a clean drain.
 *
 * <p>Every pool created here is closed in a {@code try}-with-resources or a {@code finally};
 * a leaked non-daemon pool would keep the surefire JVM alive after the suite finished.
 */
@Timeout(30)
@DisplayName("PipelineExecutors")
class PipelineExecutorsTest {

    /** Long enough that a clean drain never races, short enough that a stuck one is quick. */
    private static final Duration DRAIN_BUDGET = Duration.ofSeconds(2L);

    @ParameterizedTest
    @DisplayName("a pool size below one is a configuration bug and is rejected")
    @CsvSource({"0, 1", "-1, 1", "1, 0", "1, -1"})
    void degeneratePoolSizesAreRejected(int consumerThreads, int aggregationParallelism) {
        // Zero reaches here only if a caller forgot to resolve aggregationParallelism=0 to
        // availableProcessors(); a ForkJoinPool with parallelism 0 accepts work and never
        // runs it, so failing loudly beats a pipeline that hangs.
        assertThrows(IllegalArgumentException.class,
                () -> new PipelineExecutors(consumerThreads, aggregationParallelism));
    }

    @Test
    @DisplayName("the message names which of the two sizes was wrong")
    void rejectionMessagesAreSpecific() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new PipelineExecutors(0, 1))
                .getMessage().contains("consumerThreads"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new PipelineExecutors(1, 0))
                .getMessage().contains("aggregationParallelism"));
    }

    @Test
    @DisplayName("the four pools are four distinct objects")
    void poolsAreSeparate() throws Exception {
        try (PipelineExecutors executors = new PipelineExecutors(2, 2)) {
            // Sharing one pool between a blocking producer, blocking consumers and fork/join
            // arithmetic is how a pipeline deadlocks with every thread parked in put().
            assertNotSame(executors.producerExecutor(), executors.consumerExecutor());
            assertNotSame(executors.consumerExecutor(), executors.dispatcherExecutor());
            assertNotSame(executors.producerExecutor(), executors.dispatcherExecutor());
        }
    }

    /**
     * The common pool has {@code cores - 1} workers, so a one-core runner gets zero and the
     * "parallel" stage runs on the caller. A dedicated pool is the only way the configured
     * parallelism means anything.
     */
    @Test
    @DisplayName("the fork/join pool is dedicated, never the JVM-wide common pool")
    void forkJoinPoolIsNotTheCommonPool() throws Exception {
        try (PipelineExecutors executors = new PipelineExecutors(2, 3)) {
            assertNotSame(ForkJoinPool.commonPool(), executors.forkJoinPool());
            assertEquals(3, executors.forkJoinPool().getParallelism(),
                    "the configured parallelism must be the pool's, not the machine's");
        }
    }

    @Test
    @DisplayName("fork/join workers carry readable names, so a thread dump names the stage")
    void forkJoinWorkersAreNamed() throws Exception {
        try (PipelineExecutors executors = new PipelineExecutors(1, 2)) {
            String name = executors.forkJoinPool()
                    .submit(() -> Thread.currentThread().getName())
                    .get(10L, TimeUnit.SECONDS);
            // pipeline-fj-0 beats ForkJoinPool-2-worker-0 at 3 a.m.
            assertTrue(name.startsWith("pipeline-fj-"), name);
        }
    }

    @Test
    @DisplayName("the consumer pool really has the requested number of threads")
    void consumerPoolIsSizedAsConfigured() throws Exception {
        int threads = 3;
        try (PipelineExecutors executors = new PipelineExecutors(threads, 1)) {
            CountDownLatch allRunning = new CountDownLatch(threads);
            CountDownLatch release = new CountDownLatch(1);
            for (int i = 0; i < threads; i++) {
                executors.consumerExecutor().execute(() -> {
                    allRunning.countDown();
                    awaitQuietly(release);
                });
            }
            // If the pool were smaller, the third task would never start and this would time
            // out — a latch says it exactly, where a sleep would only say it usually.
            assertTrue(allRunning.await(10L, TimeUnit.SECONDS), "all " + threads + " should run at once");
            release.countDown();
        }
    }

    @Test
    @DisplayName("the producer and dispatcher pools are single-threaded")
    void singleThreadedPoolsRunOneTaskAtATime() throws Exception {
        try (PipelineExecutors executors = new PipelineExecutors(1, 1)) {
            for (ExecutorService single : new ExecutorService[] {
                executors.producerExecutor(), executors.dispatcherExecutor()}) {
                AtomicReference<String> first = new AtomicReference<>();
                AtomicReference<String> second = new AtomicReference<>();
                single.execute(() -> first.set(Thread.currentThread().getName()));
                single.submit(() -> second.set(Thread.currentThread().getName()))
                        .get(10L, TimeUnit.SECONDS);
                assertEquals(first.get(), second.get(), "one thread, so both tasks ran on it");
            }
        }
    }

    @Test
    @DisplayName("idle pools shut down cleanly and report true")
    void cleanShutdownReportsTrue() throws Exception {
        PipelineExecutors executors = new PipelineExecutors(2, 2);
        try {
            assertTrue(executors.shutdownAndAwait(DRAIN_BUDGET));
            assertTrue(executors.producerExecutor().isTerminated());
            assertTrue(executors.consumerExecutor().isTerminated());
            assertTrue(executors.dispatcherExecutor().isTerminated());
            assertTrue(executors.forkJoinPool().isTerminated());
        } finally {
            executors.close();
        }
    }

    @Test
    @DisplayName("a task already running to completion is waited for, not cut off")
    void inFlightWorkFinishesWithinTheDrainWindow() throws Exception {
        PipelineExecutors executors = new PipelineExecutors(1, 1);
        CountDownLatch finished = new CountDownLatch(1);
        try {
            executors.consumerExecutor().execute(() -> {
                sleepQuietly(150L);
                finished.countDown();
            });
            assertTrue(executors.shutdownAndAwait(DRAIN_BUDGET), "150 ms of work fits the window");
            assertEquals(0L, finished.getCount(), "the task ran to completion rather than being dropped");
        } finally {
            executors.close();
        }
    }

    /**
     * {@code shutdownNow()} interrupts; it does not stop. A task in an uninterruptible loop
     * is still running when the escalation window closes, and reporting {@code true} there
     * would let {@code main} print a clean report over a JVM that will not exit.
     */
    @Test
    @DisplayName("a task that ignores interruption makes shutdownAndAwait report false")
    void anUninterruptibleTaskIsReported() throws Exception {
        PipelineExecutors executors = new PipelineExecutors(1, 1);
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            executors.consumerExecutor().execute(() -> {
                running.countDown();
                // Deliberately swallows the interrupt and keeps going, which is what a tight
                // arithmetic loop with no blocking call does in practice.
                while (release.getCount() > 0L) {
                    Thread.onSpinWait();
                }
            });
            assertTrue(running.await(10L, TimeUnit.SECONDS));
            assertFalse(executors.shutdownAndAwait(Duration.ofMillis(500L)),
                    "the pool has not terminated, so the answer must be false");
            assertFalse(executors.consumerExecutor().isTerminated());
        } finally {
            release.countDown();
            executors.close();
        }
    }

    @Test
    @DisplayName("forceShutdown drops queued tasks that never started")
    void forceShutdownDropsQueuedWork() throws Exception {
        PipelineExecutors executors = new PipelineExecutors(1, 1);
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondRan = new CountDownLatch(1);
        try {
            executors.consumerExecutor().execute(() -> {
                blocked.countDown();
                awaitQuietly(release);
            });
            assertTrue(blocked.await(10L, TimeUnit.SECONDS));
            executors.consumerExecutor().execute(secondRan::countDown);

            executors.forceShutdown();
            release.countDown();
            assertTrue(executors.consumerExecutor().awaitTermination(10L, TimeUnit.SECONDS));
            assertEquals(1L, secondRan.getCount(), "the queued task must not have run");
        } finally {
            release.countDown();
            executors.close();
        }
    }

    @Test
    @DisplayName("close() is idempotent, so try-with-resources after an explicit shutdown is safe")
    void closeIsIdempotent() throws Exception {
        PipelineExecutors executors = new PipelineExecutors(1, 1);
        assertTrue(executors.shutdownAndAwait(DRAIN_BUDGET));
        executors.close();
        executors.close();
        assertTrue(executors.forkJoinPool().isTerminated());
    }

    @Test
    @DisplayName("shutdownAndAwait on already-terminated pools still reports true")
    void repeatedShutdownIsStillClean() throws Exception {
        PipelineExecutors executors = new PipelineExecutors(1, 1);
        try {
            assertTrue(executors.shutdownAndAwait(DRAIN_BUDGET));
            assertTrue(executors.shutdownAndAwait(DRAIN_BUDGET), "idempotent, not an error");
        } finally {
            executors.close();
        }
    }

    @Test
    @DisplayName("a zero timeout is legal and reports on the pools as they stand")
    void zeroTimeoutDoesNotThrow() throws Exception {
        PipelineExecutors executors = new PipelineExecutors(1, 1);
        try {
            // The invariant is that the budget arithmetic never produces a negative wait --
            // awaitTermination(-1) throws -- so a zero budget must return, not blow up. What
            // it returns depends on how fast idle pools terminate, which is not a contract.
            executors.shutdownAndAwait(Duration.ZERO);
            assertTrue(executors.producerExecutor().isShutdown());
            assertTrue(executors.forkJoinPool().isShutdown());
        } finally {
            executors.close();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
