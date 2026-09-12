package com.example.concurrency.producerconsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class BoundedBufferTest {

    @Test
    void validatesCapacityElementsAndTimeouts() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new BoundedBuffer<>(0));
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(1);
        assertThrows(NullPointerException.class, () -> buffer.put(null));
        assertThrows(NullPointerException.class, () -> buffer.offer(null, Duration.ZERO));
        assertThrows(NullPointerException.class, () -> buffer.offer(1, null));
        assertThrows(NullPointerException.class, () -> buffer.poll(null));
        assertThrows(IllegalArgumentException.class, () -> buffer.offer(
                1, Duration.ofNanos(-1)));
        assertThrows(IllegalArgumentException.class, () -> buffer.poll(
                Duration.ofNanos(-1)));
    }

    @Test
    void preservesFifoOrderAndHonorsZeroTimeouts() throws Exception {
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(2);
        buffer.put(1);
        buffer.put(2);

        assertFalse(buffer.offer(3, Duration.ZERO));
        assertEquals(1, buffer.take());
        assertEquals(2, buffer.take());
        assertTrue(buffer.poll(Duration.ZERO).isEmpty());
    }

    @Test
    @Timeout(10)
    void multipleProducersAndConsumersConserveItems() throws Exception {
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(7);
        int itemsPerProducer = 100;
        List<Integer> consumed = java.util.Collections.synchronizedList(new ArrayList<>());

        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            Future<?> producerOne = executor.submit(
                    () -> produce(buffer, 0, itemsPerProducer));
            Future<?> producerTwo = executor.submit(
                    () -> produce(buffer, itemsPerProducer, itemsPerProducer));
            Future<?> consumerOne = executor.submit(
                    () -> consume(buffer, consumed, itemsPerProducer));
            Future<?> consumerTwo = executor.submit(
                    () -> consume(buffer, consumed, itemsPerProducer));

            for (Future<?> future : List.of(producerOne, producerTwo, consumerOne, consumerTwo)) {
                future.get(5, TimeUnit.SECONDS);
            }
        }

        assertEquals(itemsPerProducer * 2, consumed.size());
        assertEquals(itemsPerProducer * 2, new HashSet<>(consumed).size());
        assertEquals(0, buffer.size());
    }

    @Test
    @Timeout(5)
    void exchangerSwapsIndependentBatchSnapshots() throws Exception {
        BatchExchanger<Integer> exchanger = new BatchExchanger<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<List<Integer>> first = executor.submit(
                    () -> exchanger.exchange(List.of(1, 2), Duration.ofSeconds(1)));
            Future<List<Integer>> second = executor.submit(
                    () -> exchanger.exchange(List.of(3, 4), Duration.ofSeconds(1)));
            assertEquals(List.of(3, 4), first.get(2, TimeUnit.SECONDS));
            assertEquals(List.of(1, 2), second.get(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void exchangerValidatesInputsBeforeWaitingForAPeer() {
        BatchExchanger<Integer> exchanger = new BatchExchanger<>();
        assertThrows(NullPointerException.class, () -> exchanger.exchange(
                null, Duration.ZERO));
        assertThrows(NullPointerException.class, () -> exchanger.exchange(
                List.of(1), null));
        assertThrows(IllegalArgumentException.class, () -> exchanger.exchange(
                List.of(1), Duration.ofNanos(-1)));
    }

    private static Void produce(BoundedBuffer<Integer> buffer, int offset, int count)
            throws InterruptedException {
        for (int value = 0; value < count; value++) {
            buffer.put(offset + value);
        }
        return null;
    }

    private static Void consume(BoundedBuffer<Integer> buffer, List<Integer> result, int count)
            throws InterruptedException {
        for (int index = 0; index < count; index++) {
            result.add(buffer.take());
        }
        return null;
    }
}
