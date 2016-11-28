package com.my;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Part5ThreadPools implements Runnable{

    private final int id;

    public Part5ThreadPools(int id){
        this.id = id;
    }

    @Override
    public void run() {
        System.out.println("Started:" + id);
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Completed:" + id);
    }

    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        for (int i = 0; i < 5; i++) {
            executor.submit(new Part5ThreadPools(i));
        }
        System.out.println("All tasks submitted");
        executor.shutdown();

        try {
            executor.awaitTermination(8, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("All tasks completed");

        //http://stackoverflow.com/questions/11520189/difference-between-shutdown-and-shutdownnow-of-executor-service

        /*In summary, you can think of it that way:

        - shutdown() will just tell the executor service that it can't accept new tasks,
        but the already submitted tasks continue to run
        - shutdownNow() will do the same AND will try to cancel the already submitted tasks
        by interrupting the relevant threads. Note that if your tasks ignore the interruption,
        shutdownNow will behave exactly the same way as shutdown.
        */
    }

}


// https://docs.oracle.com/javase/tutorial/essential/concurrency/pools.html
//
//
