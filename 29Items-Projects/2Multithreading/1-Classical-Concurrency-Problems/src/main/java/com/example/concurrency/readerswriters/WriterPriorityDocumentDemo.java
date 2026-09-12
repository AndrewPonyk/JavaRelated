package com.example.concurrency.readerswriters;

/** Standalone Condition-based writer-priority demonstration. */
public final class WriterPriorityDocumentDemo {
    private WriterPriorityDocumentDemo() {
    }

    public static void main(String[] args) throws Exception {
        ReaderWriterDemoRunner.run("writer priority", new WriterPriorityDocument("version-0"));
    }
}
