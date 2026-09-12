package com.example.concurrency.readerswriters;

/** Minimal contract shared by reader/writer synchronization strategies. */
public interface SharedDocument {
    String read() throws InterruptedException;

    void write(String newContent) throws InterruptedException;
}
