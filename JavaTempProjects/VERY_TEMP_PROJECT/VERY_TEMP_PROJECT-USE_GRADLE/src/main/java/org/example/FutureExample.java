package org.example;

import java.util.concurrent.*;

public class FutureExample {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(10);

        Callable<Long> task = () -> {
            Thread.sleep(6000);
            return 20 * 100L;
        };

        Future<Long> resultFuture = executor.submit(task);
        System.out.println("Doing some other work");

        if (resultFuture.isDone()) {
            Long result = resultFuture.get();
            System.out.println("Result: " + result);
        }

        System.out.println("Doing another important work");

        Thread.sleep(7000);
        if (resultFuture.isDone()) {
            Long result = resultFuture.get();
            System.out.println("Now future is completed Result: " + result);
        }
        executor.shutdown();
    }
}

