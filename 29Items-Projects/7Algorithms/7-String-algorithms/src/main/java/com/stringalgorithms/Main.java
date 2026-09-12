package com.stringalgorithms;

import com.stringalgorithms.demo.ConsoleDemo;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    public static int run(String[] args) {
        try {
            ConsoleDemo.run(args);
            return 0;
        } catch (IllegalArgumentException ex) {
            System.err.printf("Error: %s%n", ex.getMessage());
            return 2;
        }
    }
}
