package com.example.trading.infrastructure.concurrent;

import com.example.trading.application.port.OrderIdGenerator;
import java.util.concurrent.atomic.AtomicLong;

public final class AtomicOrderIdGenerator implements OrderIdGenerator {
    private final AtomicLong next;

    public AtomicOrderIdGenerator() {
        this(1L);
    }

    public AtomicOrderIdGenerator(long firstId) {
        if (firstId <= 0) {
            throw new IllegalArgumentException("firstId must be positive");
        }
        next = new AtomicLong(firstId);
    }

    @Override
    public long nextId() {
        long id = next.getAndIncrement();
        if (id <= 0) {
            throw new IllegalStateException("order ID sequence exhausted");
        }
        return id;
    }
}
