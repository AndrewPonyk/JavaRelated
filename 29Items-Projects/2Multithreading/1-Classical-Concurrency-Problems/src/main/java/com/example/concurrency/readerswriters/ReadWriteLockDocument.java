package com.example.concurrency.readerswriters;

import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Shared document backed by a configurable fair or non-fair ReadWriteLock. */
public final class ReadWriteLockDocument implements SharedDocument {
    private final ReentrantReadWriteLock lock;
    private final Lock readLock;
    private final Lock writeLock;
    private String content;

    public ReadWriteLockDocument(String initialContent, boolean fair) {
        content = Objects.requireNonNull(initialContent, "initialContent");
        lock = new ReentrantReadWriteLock(fair);
        readLock = lock.readLock();
        writeLock = lock.writeLock();
    }

    @Override
    public String read() throws InterruptedException {
        readLock.lockInterruptibly();
        try {
            return content;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void write(String newContent) throws InterruptedException {
        Objects.requireNonNull(newContent, "newContent");
        writeLock.lockInterruptibly();
        try {
            content = newContent;
        } finally {
            writeLock.unlock();
        }
    }

    public boolean isFair() {
        return lock.isFair();
    }
}
