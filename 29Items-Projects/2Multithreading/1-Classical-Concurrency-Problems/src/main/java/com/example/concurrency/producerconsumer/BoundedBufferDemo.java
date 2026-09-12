package com.example.concurrency.producerconsumer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Standalone bounded-buffer demonstration with two producers and one consumer. */
public final class BoundedBufferDemo {
    private BoundedBufferDemo() {
    }

    public static void main(String[] args) throws Exception {
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(3);
        List<Integer> consumed = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            Future<?> firstProducer = executor.submit(() -> produce(buffer, 1));
            Future<?> secondProducer = executor.submit(() -> produce(buffer, 101));
            Future<?> consumer = executor.submit(() -> {
                for (int count = 0; count < 10; count++) {
                    consumed.add(buffer.take());
                }
                return null;
            });
            for (Future<?> future : List.of(firstProducer, secondProducer, consumer)) {
                future.get(3, TimeUnit.SECONDS);
            }
        }

        boolean allItemsUnique = new HashSet<>(consumed).size() == 10;
        System.out.printf(
                "Producer-Consumer: produced=10, consumed=%d, remaining=%d, allItemsPreserved=%s%n",
                consumed.size(),
                buffer.size(),
                allItemsUnique);
    }

    private static Void produce(BoundedBuffer<Integer> buffer, int firstValue)
            throws InterruptedException {
        for (int offset = 0; offset < 5; offset++) {
            buffer.put(firstValue + offset);
        }
        return null;
    }
}
