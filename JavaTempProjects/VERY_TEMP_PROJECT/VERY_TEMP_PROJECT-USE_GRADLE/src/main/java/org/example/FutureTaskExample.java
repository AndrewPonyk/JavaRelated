package org.example;

import java.util.concurrent.FutureTask;

public class FutureTaskExample {
    public static void main(String[] args) {
        FutureTask<Long> futureTask = new FutureTask<>(() -> {
            Thread.sleep(6000);
            return 20 * 100L;
        });


        System.out.println("Doing other things1...");
        System.out.println("Doing other things2...");
        System.out.println("Doing other things3...");
        System.out.println("Doing other things4...");

    }
}
