package org.example;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public class FutureTaskExample {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        FutureTask<Long> futureTask = new FutureTask<>(() -> {
            Thread.sleep(6000); // Simulate a long-running task
            return 20 * 100L;
        });

        // Start the background task
        new Thread(futureTask).start();

        System.out.println("Doing other things1...");
        System.out.println("Doing other things2...");

        // Check if the task is done (optional)
        if (futureTask.isDone()) {
            System.out.println("Background task is finished, retrieving result...");
            Long result = futureTask.get(); // This will wait for the task to finish (blocks)
            System.out.println("Result: " + result);
        } else {
            System.out.println("Background task is still running...");
            // You can do other things while waiting for the task to finish
        }

        Thread.sleep(1000);
        System.out.println("Doing other things3...");
        //DONT WANT TO WAITE MORE
        System.out.println("Can wait more, so block current THREAD and wait value from future: " + futureTask.get());
        System.out.println("Doing other things4...");
    }
}
