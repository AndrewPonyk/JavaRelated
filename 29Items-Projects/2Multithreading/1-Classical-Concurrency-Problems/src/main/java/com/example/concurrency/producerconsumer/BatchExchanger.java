package com.example.concurrency.producerconsumer;

import com.example.concurrency.common.Timeouts;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Two-party batch handoff example backed by Exchanger. */
public final class BatchExchanger<T> {
    private final Exchanger<List<T>> exchanger = new Exchanger<>();

    public List<T> exchange(List<T> outgoing, Duration timeout)
            throws InterruptedException, TimeoutException {
        Objects.requireNonNull(outgoing, "outgoing");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        List<T> snapshot = List.copyOf(outgoing);
        List<T> incoming = exchanger.exchange(
                snapshot, Timeouts.toNanos(timeout), TimeUnit.NANOSECONDS);
        return new ArrayList<>(incoming);
    }
}
