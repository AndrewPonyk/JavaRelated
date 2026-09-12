package com.example.concurrency.dining;

/** Standalone arbitrator Dining Philosophers demonstration. */
public final class ArbitratorDiningTableDemo {
    private ArbitratorDiningTableDemo() {
    }

    public static void main(String[] args) throws Exception {
        DiningDemoRunner.run("N-1 arbitrator", new ArbitratorDiningTable(5));
    }
}
