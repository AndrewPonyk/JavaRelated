package com.example.concurrency.producerconsumer;

import com.example.concurrency.common.Timeouts;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** Generic bounded FIFO implemented with explicit not-empty and not-full predicates. */
public final class BoundedBuffer<T> {
    private final Deque<T> elements;
    private final int capacity;
    private final ReentrantLock lock;
    private final Condition notEmpty;
    private final Condition notFull;

    public BoundedBuffer(int capacity) {
        this(capacity, true);
    }

    public BoundedBuffer(int capacity, boolean fair) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        elements = new ArrayDeque<>(capacity);
        lock = new ReentrantLock(fair);
        notEmpty = lock.newCondition();
        notFull = lock.newCondition();
    }

    public void put(T element) throws InterruptedException {
        Objects.requireNonNull(element, "element");
        lock.lockInterruptibly();
        try {
            while (elements.size() == capacity) {
                notFull.await();
            }
            elements.addLast(element);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    public T take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (elements.isEmpty()) {
                notEmpty.await();
            }
            T result = elements.removeFirst();
            notFull.signal();
            return result;
        } finally {
            lock.unlock();
        }
    }

    public boolean offer(T element, Duration timeout) throws InterruptedException {
        Objects.requireNonNull(element, "element");
        long remaining = Timeouts.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (elements.size() == capacity) {
                if (remaining <= 0L) {
                    return false;
                }
                remaining = notFull.awaitNanos(remaining);
            }
            elements.addLast(element);
            notEmpty.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }

    public Optional<T> poll(Duration timeout) throws InterruptedException {
        long remaining = Timeouts.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (elements.isEmpty()) {
                if (remaining <= 0L) {
                    return Optional.empty();
                }
                remaining = notEmpty.awaitNanos(remaining);
            }
            T result = elements.removeFirst();
            notFull.signal();
            return Optional.of(result);
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return elements.size();
        } finally {
            lock.unlock();
        }
    }

    public int capacity() {
        return capacity;
    }

}
