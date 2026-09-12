package com.example.concurrency.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ConcurrencyDemoTest {

    @Test
    void rejectsUnknownAndNullCommandArrays() {
        assertThrows(NullPointerException.class, () -> ConcurrencyDemo.main(null));
        assertThrows(IllegalArgumentException.class, () -> ConcurrencyDemo.main(
                new String[] {"not-a-command"}));
    }

    @Test
    @Timeout(15)
    void allSafeCommandRunsEverySafeDemoWithConciseOutput() throws Exception {
        String output = captureOutput(() -> ConcurrencyDemo.main(new String[] {"all-safe"}));

        assertEquals(16, output.lines().count());
        assertTrue(output.contains("Dining Philosophers [resource hierarchy]"));
        assertTrue(output.contains("Sleeping Barber"));
        assertTrue(output.contains("H2O Builder"));
        assertTrue(output.contains("Bank Transfer [timed tryLock]"));
    }

    @Test
    void listCommandDocumentsEveryStandaloneDemo() throws Exception {
        String output = captureOutput(() -> ConcurrencyDemo.main(new String[] {"list"}));

        assertTrue(output.contains("dining-deadlock"));
        assertTrue(output.contains("readers-writer-priority"));
        assertTrue(output.contains("bank-try-lock"));
        assertTrue(output.contains("all-safe"));
    }

    private static String captureOutput(ThrowingAction action) throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream replacement = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(replacement);
            action.run();
        } finally {
            System.setOut(original);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
