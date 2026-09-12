package com.example.concurrency.readerswriters;

/** Standalone fair ReentrantReadWriteLock demonstration. */
public final class FairReadWriteLockDocumentDemo {
    private FairReadWriteLockDocumentDemo() {
    }

    public static void main(String[] args) throws Exception {
        ReaderWriterDemoRunner.run(
                "fair ReadWriteLock", new ReadWriteLockDocument("version-0", true));
    }
}
