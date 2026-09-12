package com.example.pipeline.infrastructure.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Thread naming, daemon status, and the handler that stops a dead thread from being silent.
 *
 * <p>{@link #namesAreUniqueUnderConcurrentCreation()} is the one with teeth: the counter is
 * an {@link java.util.concurrent.atomic.AtomicInteger} because a fixed pool creates its
 * threads from whichever caller submits first, and two threads called
 * {@code pipeline-filter-0} in a stack trace is worse than no names at all.
 *
 * <p>Nothing here starts a thread that does real work — {@code newThread} returns an
 * unstarted {@link Thread}, and asserting on it before {@code start()} keeps the test free
 * of both timing and stray live threads.
 */
@Timeout(15)
@DisplayName("NamedThreadFactory")
class NamedThreadFactoryTest {

    private static final Runnable NOTHING = () -> { };

    @Test
    @DisplayName("a null prefix is rejected at construction")
    void nullPrefixIsRejected() {
        assertThrows(NullPointerException.class, () -> new NamedThreadFactory(null));
        assertThrows(NullPointerException.class, () -> new NamedThreadFactory(null, true));
    }

    @Test
    @DisplayName("a null runnable is rejected rather than producing a thread that does nothing")
    void nullRunnableIsRejected() {
        assertThrows(NullPointerException.class, () -> new NamedThreadFactory("p").newThread(null));
    }

    @Test
    @DisplayName("threads are numbered from zero, in creation order")
    void namesAreSequential() {
        NamedThreadFactory factory = new NamedThreadFactory("pipeline-filter");
        assertEquals("pipeline-filter-0", factory.newThread(NOTHING).getName());
        assertEquals("pipeline-filter-1", factory.newThread(NOTHING).getName());
        assertEquals("pipeline-filter-2", factory.newThread(NOTHING).getName());
    }

    @Test
    @DisplayName("created() counts what the factory handed out")
    void createdCountsThreads() {
        NamedThreadFactory factory = new NamedThreadFactory("pipeline-filter");
        assertEquals(0, factory.created(), "nothing created yet");
        factory.newThread(NOTHING);
        factory.newThread(NOTHING);
        assertEquals(2, factory.created());
    }

    @Test
    @DisplayName("two factories number independently, so pools do not share a sequence")
    void countersArePerFactory() {
        assertEquals("a-0", new NamedThreadFactory("a").newThread(NOTHING).getName());
        assertEquals("b-0", new NamedThreadFactory("b").newThread(NOTHING).getName());
    }

    @Test
    @DisplayName("threads are non-daemon by default, so a draining stage keeps the JVM alive")
    void defaultThreadsAreNonDaemon() {
        // The poison-pill shutdown only works if the JVM waits for the stage that is still
        // aggregating; a daemon default would exit mid-batch and lose the work.
        assertFalse(new NamedThreadFactory("pipeline-filter").newThread(NOTHING).isDaemon());
    }

    @Test
    @DisplayName("daemon threads are available for the dashboard, which has no state to drain")
    void daemonFlagIsHonoured() {
        assertTrue(new NamedThreadFactory("pipeline-dashboard", true).newThread(NOTHING).isDaemon());
    }

    @Test
    @DisplayName("every thread gets the logging uncaught-exception handler")
    void handlerIsInstalled() {
        // A stage thread that dies silently looks like a stage that stopped consuming, which
        // is a much harder diagnosis than a stack trace in the log.
        Thread thread = new NamedThreadFactory("pipeline-filter").newThread(NOTHING);
        assertNotNull(thread.getUncaughtExceptionHandler());
        assertNotSame(thread.getThreadGroup(), thread.getUncaughtExceptionHandler(),
                "the group's default handler is not the one that logs");
    }

    @Test
    @DisplayName("the returned thread is not started, so the caller decides when it runs")
    void threadsAreReturnedUnstarted() {
        assertEquals(Thread.State.NEW, new NamedThreadFactory("p").newThread(NOTHING).getState());
    }

    @Test
    @DisplayName("the handler runs on an actual failure and does not rethrow")
    void handlerSwallowsTheFailureAfterLoggingIt() throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        Thread thread = new NamedThreadFactory("pipeline-boom").newThread(() -> {
            throw new IllegalStateException("deliberate: exercising the uncaught handler");
        });
        // The handler is the last thing to run on a dying thread, so "the thread terminated"
        // is the observable proof that it ran and did not itself blow up.
        thread.setUncaughtExceptionHandler(wrap(thread.getUncaughtExceptionHandler(), finished));
        thread.start();
        assertTrue(finished.await(5, TimeUnit.SECONDS), "the handler should have been invoked");
        thread.join(TimeUnit.SECONDS.toMillis(5));
        assertFalse(thread.isAlive());
    }

    private static Thread.UncaughtExceptionHandler wrap(Thread.UncaughtExceptionHandler delegate,
            CountDownLatch finished) {
        return (t, e) -> {
            delegate.uncaughtException(t, e);
            finished.countDown();
        };
    }

    /**
     * A fixed pool creates its threads lazily from whichever task arrives first, so the
     * factory is called from several threads at once. Duplicate names would make a thread
     * dump ambiguous exactly when it matters.
     */
    @Test
    @DisplayName("names stay unique when several pool threads are created at once")
    void namesAreUniqueUnderConcurrentCreation() throws Exception {
        int threads = 8;
        NamedThreadFactory factory = new NamedThreadFactory("pipeline-race");
        Set<String> names = ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    try {
                        start.await();
                        names.add(factory.newThread(NOTHING).getName());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS), "all creators should finish");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(threads, names.size(), "duplicate thread names: " + names);
        assertEquals(threads, factory.created());
    }
}
