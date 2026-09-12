package com.example.pipeline.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.pipeline.domain.AggregateResult;
import com.example.pipeline.domain.AggregateSnapshot;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The default repository: last-write-wins over an {@link java.util.concurrent.atomic.AtomicReference}.
 *
 * <p>{@link #findLatestNeverReturnsNull()} is the one worth having — the port returns
 * {@link Optional}, and an implementation that returned {@code null} before the first save
 * would blow up at whichever call site forgot to check, which is exactly the class of bug
 * {@code Optional} exists to prevent.
 *
 * <p>{@link #concurrentSavesAreVisibleAndNeverTornOrLost()} pins the thread-safety the port
 * documents. It asserts that <em>some</em> writer's snapshot is visible whole, not which
 * one: with concurrent last-write-wins there is no deterministic winner, and a test that
 * demanded one would be asserting a guarantee the class does not make.
 */
@Timeout(20)
@DisplayName("InMemoryAggregateRepository")
class InMemoryAggregateRepositoryTest {

    private InMemoryAggregateRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAggregateRepository();
    }

    private static AggregateSnapshot snapshotOf(String sensorId, long count) {
        return new AggregateSnapshot(Map.of(sensorId,
                new AggregateResult(sensorId, count, 1.0, 9.0, count * 5.0)));
    }

    @Test
    @DisplayName("before the first save there is nothing to find")
    void findLatestNeverReturnsNull() {
        Optional<AggregateSnapshot> found = repository.findLatest();
        assertTrue(found.isEmpty(), "an empty Optional, never a null");
    }

    @Test
    @DisplayName("a null snapshot is rejected rather than stored")
    void nullSnapshotIsRejected() {
        assertThrows(NullPointerException.class, () -> repository.save(null));
        assertTrue(repository.findLatest().isEmpty(), "a rejected save must not clear the store either");
    }

    @Test
    @DisplayName("a saved snapshot is returned as the same instance")
    void saveThenFind() {
        AggregateSnapshot snapshot = snapshotOf("sensor-a", 3L);
        repository.save(snapshot);
        // Same instance: the snapshot is immutable, so defensive copying here would be waste.
        assertSame(snapshot, repository.findLatest().orElseThrow());
    }

    @Test
    @DisplayName("the second save replaces the first - this is a latest-only store")
    void lastWriteWins() {
        repository.save(snapshotOf("sensor-a", 1L));
        AggregateSnapshot second = snapshotOf("sensor-b", 2L);
        repository.save(second);
        assertSame(second, repository.findLatest().orElseThrow());
    }

    @Test
    @DisplayName("saving an empty snapshot is legal - a run that filtered everything out")
    void emptySnapshotIsStorable() {
        repository.save(AggregateSnapshot.empty());
        // Present-but-empty and absent are different states, and the difference is
        // "the run finished with nothing to show" versus "the run never got that far".
        assertTrue(repository.findLatest().isPresent());
        assertTrue(repository.findLatest().orElseThrow().isEmpty());
    }

    @Test
    @DisplayName("clear() forgets the snapshot, restoring the pre-save state")
    void clearForgetsTheSnapshot() {
        repository.save(snapshotOf("sensor-a", 1L));
        repository.clear();
        assertTrue(repository.findLatest().isEmpty());
    }

    @Test
    @DisplayName("clear() on an empty repository is a no-op, not a failure")
    void clearIsIdempotent() {
        repository.clear();
        repository.clear();
        assertTrue(repository.findLatest().isEmpty());
    }

    @Test
    @DisplayName("repeated reads see the same value - findLatest does not consume")
    void findLatestIsNonDestructive() {
        repository.save(snapshotOf("sensor-a", 4L));
        assertSame(repository.findLatest().orElseThrow(), repository.findLatest().orElseThrow());
    }

    /**
     * The port is documented as thread-safe, and in a real run the aggregation dispatcher
     * writes while the dashboard thread reads. Publishing a whole immutable value is what
     * makes a torn read impossible; this test would still pass against a plain field on x86
     * most of the time, so its value is in the "never absent afterwards" assertion, which a
     * non-atomic clear/set pair could genuinely violate.
     */
    @Test
    @DisplayName("concurrent saves and reads never yield a torn or missing snapshot")
    void concurrentSavesAreVisibleAndNeverTornOrLost() throws Exception {
        int writers = 4;
        int savesPerWriter = 200;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger tornReads = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(writers + 1);
        try {
            for (int w = 0; w < writers; w++) {
                String sensorId = "sensor-" + w;
                pool.execute(() -> {
                    awaitQuietly(start);
                    for (int i = 1; i <= savesPerWriter; i++) {
                        repository.save(snapshotOf(sensorId, i));
                    }
                });
            }
            pool.execute(() -> {
                awaitQuietly(start);
                for (int i = 0; i < savesPerWriter * writers; i++) {
                    // Every snapshot ever saved holds exactly one sensor whose count matches
                    // its sum; anything else would mean a reader saw a half-built value.
                    repository.findLatest().ifPresent(snapshot -> {
                        if (snapshot.sensorCount() != 1) {
                            tornReads.incrementAndGet();
                        }
                    });
                }
            });
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "writers should finish promptly");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(0, tornReads.get(), "a reader observed a partially published snapshot");
        assertTrue(repository.findLatest().isPresent(), "the last write must be visible");
        assertEquals(1, repository.findLatest().orElseThrow().sensorCount());
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
