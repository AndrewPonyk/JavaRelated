package com.example.concurrency.readerswriters;

import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** Condition-based policy that stops admitting new readers once a writer is waiting. */
public final class WriterPriorityDocument implements SharedDocument {
    private final ReentrantLock stateLock = new ReentrantLock(true);
    private final Condition readersMayEnter = stateLock.newCondition();
    private final Condition writersMayEnter = stateLock.newCondition();
    private int activeReaders;
    private int waitingWriters;
    private boolean writerActive;
    private String content;

    public WriterPriorityDocument(String initialContent) {
        content = Objects.requireNonNull(initialContent, "initialContent");
    }

    @Override
    public String read() throws InterruptedException {
        beginRead();
        try {
            return content;
        } finally {
            endRead();
        }
    }

    @Override
    public void write(String newContent) throws InterruptedException {
        Objects.requireNonNull(newContent, "newContent");
        beginWrite();
        try {
            content = newContent;
        } finally {
            endWrite();
        }
    }

    private void beginRead() throws InterruptedException {
        stateLock.lockInterruptibly();
        try {
            while (writerActive || waitingWriters > 0) {
                readersMayEnter.await();
            }
            activeReaders++;
        } finally {
            stateLock.unlock();
        }
    }

    private void endRead() {
        stateLock.lock();
        try {
            activeReaders--;
            if (activeReaders == 0) {
                writersMayEnter.signal();
            }
        } finally {
            stateLock.unlock();
        }
    }

    private void beginWrite() throws InterruptedException {
        stateLock.lockInterruptibly();
        boolean admitted = false;
        waitingWriters++;
        try {
            while (writerActive || activeReaders > 0) {
                writersMayEnter.await();
            }
            writerActive = true;
            admitted = true;
        } finally {
            waitingWriters--;
            if (!admitted && waitingWriters == 0) {
                readersMayEnter.signalAll();
            }
            stateLock.unlock();
        }
    }

    private void endWrite() {
        stateLock.lock();
        try {
            writerActive = false;
            if (waitingWriters > 0) {
                writersMayEnter.signal();
            } else {
                readersMayEnter.signalAll();
            }
        } finally {
            stateLock.unlock();
        }
    }
}
