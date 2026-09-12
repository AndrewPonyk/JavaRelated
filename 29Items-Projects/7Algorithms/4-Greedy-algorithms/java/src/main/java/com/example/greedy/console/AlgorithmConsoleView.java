package com.example.greedy.console;

import java.util.function.Supplier;

public final class AlgorithmConsoleView {
    public void section(String title) {
        System.out.printf("%n=== %s ===%n", title);
    }

    public void loading(String label) {
        System.out.printf("Running %s...%n", label);
    }

    public void result(String label, Object value, String proofNote) {
        System.out.printf("%s: %s%n", label, value);
        System.out.printf("Why greedy works: %s%n", proofNote);
    }

    public void error(String label, RuntimeException exception) {
        System.out.printf("%s failed: %s%n", label, exception.getMessage());
    }

    public void runDemo(String label, Supplier<Object> action, String proofNote) {
        section(label);
        loading(label);
        try {
            result("Result", action.get(), proofNote);
        } catch (IllegalArgumentException exception) {
            error(label, exception);
        }
    }
}
