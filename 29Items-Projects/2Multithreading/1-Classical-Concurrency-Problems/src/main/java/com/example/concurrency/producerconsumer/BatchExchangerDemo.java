package com.example.concurrency.producerconsumer;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Standalone two-party Exchanger demonstration. */
public final class BatchExchangerDemo {
    private BatchExchangerDemo() {
    }

    public static void main(String[] args) throws Exception {
        BatchExchanger<String> exchanger = new BatchExchanger<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<List<String>> first = executor.submit(() -> exchanger.exchange(
                    List.of("batch-A1", "batch-A2"), Duration.ofSeconds(1)));
            Future<List<String>> second = executor.submit(() -> exchanger.exchange(
                    List.of("batch-B1"), Duration.ofSeconds(1)));

            System.out.printf(
                    "Exchanger: partyAReceived=%s, partyBReceived=%s%n",
                    first.get(2, TimeUnit.SECONDS),
                    second.get(2, TimeUnit.SECONDS));
        }
    }
}
