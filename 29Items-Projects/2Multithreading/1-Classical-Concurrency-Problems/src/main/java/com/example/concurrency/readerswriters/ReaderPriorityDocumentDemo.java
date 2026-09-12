package com.example.concurrency.readerswriters;

/** Standalone intrinsic-monitor reader-priority demonstration. */
public final class ReaderPriorityDocumentDemo {
    private ReaderPriorityDocumentDemo() {
    }

    public static void main(String[] args) throws Exception {
        ReaderWriterDemoRunner.run("reader priority", new ReaderPriorityDocument("version-0"));
    }
}
