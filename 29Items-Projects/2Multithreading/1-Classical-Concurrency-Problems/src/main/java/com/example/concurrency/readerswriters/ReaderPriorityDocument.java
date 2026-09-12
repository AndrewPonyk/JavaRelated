package com.example.concurrency.readerswriters;

import java.util.Objects;

/**
 * Intrinsic-monitor reader-priority solution. New readers may enter while a writer waits,
 * which is intentional for demonstrating possible writer starvation.
 */
public final class ReaderPriorityDocument implements SharedDocument {
    private int activeReaders;
    private boolean writerActive;
    private String content;

    public ReaderPriorityDocument(String initialContent) {
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

    private synchronized void beginRead() throws InterruptedException {
        while (writerActive) {
            wait();
        }
        activeReaders++;
    }

    private synchronized void endRead() {
        activeReaders--;
        if (activeReaders == 0) {
            notifyAll();
        }
    }

    private synchronized void beginWrite() throws InterruptedException {
        while (writerActive || activeReaders > 0) {
            wait();
        }
        writerActive = true;
    }

    private synchronized void endWrite() {
        writerActive = false;
        notifyAll();
    }
}
