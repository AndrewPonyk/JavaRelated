package com.example.concurrency.readerswriters;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared bounded workload for the Readers-Writers policy demos. */
final class ReaderWriterDemoRunner {
    private ReaderWriterDemoRunner() {
    }

    static void run(String policy, SharedDocument document) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();

        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            Future<?> writer = executor.submit(() -> {
                start.await();
                for (int version = 1; version <= 5; version++) {
                    document.write("version-" + version);
                }
                return null;
            });
            Future<?> firstReader = executor.submit(() -> readTenTimes(start, document, reads));
            Future<?> secondReader = executor.submit(() -> readTenTimes(start, document, reads));
            start.countDown();
            for (Future<?> future : List.of(writer, firstReader, secondReader)) {
                future.get(3, TimeUnit.SECONDS);
            }
        }

        System.out.printf(
                "Readers-Writers [%s]: reads=%d, writes=5, finalValue=%s%n",
                policy,
                reads.get(),
                document.read());
    }

    private static Void readTenTimes(
            CountDownLatch start,
            SharedDocument document,
            AtomicInteger reads) throws InterruptedException {
        start.await();
        for (int count = 0; count < 10; count++) {
            document.read();
            reads.incrementAndGet();
        }
        return null;
    }
}
