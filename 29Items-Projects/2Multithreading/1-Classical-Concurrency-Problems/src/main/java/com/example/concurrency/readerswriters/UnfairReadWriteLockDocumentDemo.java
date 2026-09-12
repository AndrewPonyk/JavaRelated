package com.example.concurrency.readerswriters;

/** Standalone non-fair ReentrantReadWriteLock demonstration. */
public final class UnfairReadWriteLockDocumentDemo {
    private UnfairReadWriteLockDocumentDemo() {
    }

    public static void main(String[] args) throws Exception {
        ReaderWriterDemoRunner.run(
                "non-fair ReadWriteLock", new ReadWriteLockDocument("version-0", false));
    }
}
