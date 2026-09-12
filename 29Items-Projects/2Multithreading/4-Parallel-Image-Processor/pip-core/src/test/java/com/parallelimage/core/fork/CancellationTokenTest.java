package com.parallelimage.core.fork;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link CancellationToken} tests, most of which exist because of one bug.
 *
 * <p>{@link CancellationToken#NONE} is a process-wide singleton that every default constructor in the
 * codebase hands out, and it used to be an ordinary token that merely nobody was <em>supposed</em> to
 * cancel. Two paths cancelled it anyway — an engine closing with no batch running, and
 * {@link CancellationToken#throwIfCancelledOrInterrupted()} on an interrupted thread — after which
 * every kernel in the JVM aborted for the rest of its life. The symptom was spectacular and the cause
 * invisible: unrelated filter tests failing with {@code CancellationException} because a completely
 * different test class had closed an engine minutes earlier.
 */
class CancellationTokenTest {

    @AfterEach
    void clearInterruptFlag() {
        // Deliberate: interruption tests leave the flag set on the shared JUnit thread, and JUnit
        // reuses it for the next test.
        Thread.interrupted();
    }

    @Test
    @DisplayName("a fresh token cancels once and stays cancelled")
    void cancelIsIdempotent() {
        CancellationToken token = new CancellationToken();

        assertFalse(token.isCancelled());
        assertDoesNotThrow(token::throwIfCancelled);
        assertTrue(token.cancel(), "the first cancel wins");
        assertFalse(token.cancel(), "a second cancel must report that it changed nothing");
        assertTrue(token.isCancelled());
        assertThrows(CancellationException.class, token::throwIfCancelled);
    }

    @Test
    @DisplayName("NONE cannot be cancelled, by anyone, ever")
    void noneIsNotCancellable() {
        assertFalse(CancellationToken.NONE.cancel(),
                "NONE.cancel() must be a no-op that admits it did nothing");
        assertFalse(CancellationToken.NONE.isCancelled());
        assertDoesNotThrow(CancellationToken.NONE::throwIfCancelled);
        assertDoesNotThrow(CancellationToken.NONE::throwIfCancelledOrInterrupted);
    }

    @Test
    @DisplayName("interruption aborts the task, restores the flag, and does not poison NONE")
    void interruptionDoesNotPoisonTheSharedToken() {
        Thread.currentThread().interrupt();

        assertThrows(CancellationException.class,
                CancellationToken.NONE::throwIfCancelledOrInterrupted);
        assertTrue(Thread.currentThread().isInterrupted(),
                "swallowing an interrupt strips information the shutdown path above needs");
        assertFalse(CancellationToken.NONE.isCancelled(),
                "an interrupted worker polling NONE must not cancel every other batch in the JVM");
    }

    @Test
    @DisplayName("interruption does cancel a per-batch token, so siblings stop too")
    void interruptionCancelsAPerBatchToken() {
        CancellationToken token = new CancellationToken();
        Thread.currentThread().interrupt();

        assertThrows(CancellationException.class, token::throwIfCancelledOrInterrupted);
        assertTrue(token.isCancelled(),
                "one interrupted worker means the batch is going down; tell the siblings");
    }

    @Test
    @DisplayName("exactly one of many concurrent cancellers is told it won")
    void onlyOneConcurrentCancellerWins() throws Exception {
        CancellationToken token = new CancellationToken();
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();

        try {
            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    start.await();
                    if (token.cancel()) {
                        winners.incrementAndGet();
                    }
                    return null;
                });
            }
            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertTrue(token.isCancelled());
        assertTrue(winners.get() == 1,
                "compareAndSet is what lets the UI log 'cancelled by user' exactly once, got "
                        + winners.get());
    }
}
