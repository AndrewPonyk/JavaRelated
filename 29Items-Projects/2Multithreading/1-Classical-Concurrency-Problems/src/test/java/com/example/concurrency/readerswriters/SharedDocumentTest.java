package com.example.concurrency.readerswriters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class SharedDocumentTest {

    @Test
    void validatesInitialAndWrittenContent() throws Exception {
        assertThrows(NullPointerException.class, () -> new ReaderPriorityDocument(null));
        assertThrows(NullPointerException.class, () -> new ReadWriteLockDocument(null, true));
        assertThrows(NullPointerException.class, () -> new WriterPriorityDocument(null));

        for (SharedDocument document : List.of(
                new ReaderPriorityDocument("value"),
                new ReadWriteLockDocument("value", true),
                new WriterPriorityDocument("value"))) {
            assertThrows(NullPointerException.class, () -> document.write(null));
        }
    }

    @Test
    void allStrategiesSupportBasicReadsAndWrites() throws Exception {
        List<SharedDocument> documents = List.of(
                new ReaderPriorityDocument("initial"),
                new ReadWriteLockDocument("initial", false),
                new ReadWriteLockDocument("initial", true),
                new WriterPriorityDocument("initial"));

        for (SharedDocument document : documents) {
            assertEquals("initial", document.read());
            document.write("updated");
            assertEquals("updated", document.read());
        }
    }

    @Test
    @Timeout(10)
    void readersAndWriterMakeProgressTogether() throws Exception {
        SharedDocument document = new WriterPriorityDocument("0");
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            Future<?> writer = executor.submit(() -> {
                for (int value = 1; value <= 100; value++) {
                    document.write(Integer.toString(value));
                }
                return null;
            });
            Future<?> readerOne = executor.submit(() -> readRepeatedly(document));
            Future<?> readerTwo = executor.submit(() -> readRepeatedly(document));
            Future<?> readerThree = executor.submit(() -> readRepeatedly(document));

            writer.get(5, TimeUnit.SECONDS);
            readerOne.get(5, TimeUnit.SECONDS);
            readerTwo.get(5, TimeUnit.SECONDS);
            readerThree.get(5, TimeUnit.SECONDS);
        }
        assertEquals("100", document.read());
    }

    @Test
    void exposesConfiguredReadWriteLockFairness() {
        assertFalse(new ReadWriteLockDocument("", false).isFair());
        assertTrue(new ReadWriteLockDocument("", true).isFair());
    }

    private static Void readRepeatedly(SharedDocument document) throws InterruptedException {
        for (int count = 0; count < 100; count++) {
            document.read();
        }
        return null;
    }
}
