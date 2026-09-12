package com.parallelimage.core.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.core.model.ImageMetadata;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link MetadataStore} tests, including the concurrency ones that matter.
 *
 * <p>A single-threaded test can never show that a {@code StampedLock} is used correctly — it only
 * shows the happy path. The load tests below run many readers against many writers and assert on the
 * <em>aggregate invariant</em> (sums always consistent with the count), because that is the property
 * an optimistic read can actually violate if {@code validate()} is misused.
 */
class MetadataStoreTest {

    @Test
    @DisplayName("an empty store reports the identity stats rather than null")
    void emptyStoreHasEmptyStats() {
        MetadataStore store = new MetadataStore();
        assertEquals(MetadataStore.Stats.EMPTY, store.stats());
        assertEquals(0, store.size());
        assertEquals(Optional.empty(), store.find("nope"));
        assertEquals(List.of(), store.snapshot());
    }

    @Test
    @DisplayName("aggregates track puts")
    void aggregatesTrackPuts() {
        MetadataStore store = new MetadataStore();
        store.put(metadata("a", 1_000, 1_000, 4_096L));
        store.put(metadata("b", 2_000, 500, 8_192L));

        MetadataStore.Stats stats = store.stats();
        assertEquals(2, stats.count());
        assertEquals(2_000_000L, stats.totalPixels());
        assertEquals(12_288L, stats.totalSourceBytes());
        assertEquals(1_000_000L, stats.maxPixels());
        assertEquals(1.0d, stats.averageMegapixels(), 1e-9d);
    }

    @Test
    @DisplayName("replacing a record backs out the old contribution instead of double-counting")
    void replacementDoesNotDoubleCount() {
        MetadataStore store = new MetadataStore();
        store.put(metadata("a", 100, 100, 10L));
        store.put(metadata("a", 200, 200, 20L));

        MetadataStore.Stats stats = store.stats();
        assertEquals(1, stats.count(), "the same jobId must not create a second entry");
        assertEquals(40_000L, stats.totalPixels());
        assertEquals(20L, stats.totalSourceBytes());
    }

    @Test
    @DisplayName("computeIfAbsent calls the supplier once and returns the same instance thereafter")
    void computeIfAbsentIsIdempotent() {
        MetadataStore store = new MetadataStore();
        AtomicInteger calls = new AtomicInteger();

        ImageMetadata first = store.computeIfAbsent("a", () -> {
            calls.incrementAndGet();
            return metadata("a", 10, 10, 1L);
        });
        ImageMetadata second = store.computeIfAbsent("a", () -> {
            calls.incrementAndGet();
            return metadata("a", 99, 99, 9L);
        });

        assertSame(first, second);
        assertEquals(1, calls.get());
        assertEquals(1, store.size());
    }

    @Test
    @DisplayName("snapshot is a stable copy: mutating the store afterwards does not change it")
    void snapshotIsIsolated() {
        MetadataStore store = new MetadataStore();
        store.put(metadata("a", 10, 10, 1L));
        List<ImageMetadata> snapshot = store.snapshot();
        store.put(metadata("b", 20, 20, 2L));

        assertEquals(1, snapshot.size());
        assertEquals(2, store.snapshot().size());
    }

    @Test
    @DisplayName("clear() resets aggregates as well as entries")
    void clearResetsEverything() {
        MetadataStore store = new MetadataStore();
        store.put(metadata("a", 10, 10, 1L));
        store.clear();
        assertEquals(0, store.size());
        assertEquals(MetadataStore.Stats.EMPTY, store.stats());
    }

    @Test
    @DisplayName("concurrent readers never observe a torn aggregate")
    void concurrentReadsAreConsistent() throws Exception {
        MetadataStore store = new MetadataStore();
        int writers = 4;
        int readers = 4;
        int perWriter = 2_000;
        ExecutorService executor = Executors.newFixedThreadPool(writers + readers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger inconsistencies = new AtomicInteger();

        try {
            for (int w = 0; w < writers; w++) {
                int writerId = w;
                executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < perWriter; i++) {
                        // Every record contributes exactly 100 pixels and 10 bytes, so any consistent
                        // observation must satisfy totalPixels == count * 100.
                        store.put(metadata("w" + writerId + "-" + i, 10, 10, 10L));
                    }
                    return null;
                });
            }
            for (int r = 0; r < readers; r++) {
                executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < 50_000; i++) {
                        MetadataStore.Stats stats = store.stats();
                        if (stats.totalPixels() != stats.count() * 100L
                                || stats.totalSourceBytes() != stats.count() * 10L) {
                            inconsistencies.incrementAndGet();
                        }
                    }
                    return null;
                });
            }
            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS), "load test did not finish");
        } finally {
            executor.shutdownNow();
        }

        assertEquals(0, inconsistencies.get(),
                "an optimistic read returned a torn aggregate — validate() is being misused");
        assertEquals(writers * perWriter, store.size());
        // Informational, but a rate this far below 1.0 would mean the optimistic path is not paying
        // for itself under this contention level (see the class javadoc on MetadataStore).
        assertTrue(store.optimisticSuccessRate() >= 0.0d && store.optimisticSuccessRate() <= 1.0d);
    }

    @Test
    @DisplayName("concurrent computeIfAbsent for the same key creates exactly one record")
    void concurrentComputeIfAbsentCreatesOne() throws Exception {
        MetadataStore store = new MetadataStore();
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger suppliers = new AtomicInteger();

        try {
            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    start.await();
                    for (int k = 0; k < 500; k++) {
                        ImageMetadata got = store.computeIfAbsent("key-" + k, () -> {
                            suppliers.incrementAndGet();
                            return metadata("shared", 10, 10, 1L);
                        });
                        assertNotNull(got);
                    }
                    return null;
                });
            }
            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertEquals(500, store.size());
        assertEquals(500, suppliers.get(),
                "the recheck after converting to a write lock is missing or wrong");
    }

    private static ImageMetadata metadata(String jobId, int width, int height, long bytes) {
        return new ImageMetadata(jobId, width, height, "png", bytes,
                BufferedImage.TYPE_INT_RGB, false, Map.of());
    }
}
