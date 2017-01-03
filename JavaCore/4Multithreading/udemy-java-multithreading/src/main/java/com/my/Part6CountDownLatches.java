package com.my;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by andrii on 27.11.16.
 */
public class Part6CountDownLatches implements Runnable{
    private CountDownLatch latch;

    public Part6CountDownLatches(CountDownLatch latch) {
        this.latch = latch;

    }

    @Override
    public void run() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("End of some long running task " + System.currentTimeMillis()/1000);
        latch.countDown();
    }

    public static void main(String[] args) {

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(3);

        for(int i=0;i<3;i++){
            executor.submit(new Part6CountDownLatches(latch));
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("TASKS COMPLETED");
        executor.shutdown();
    }
}

//http://tutorials.jenkov.com/java-util-concurrent/countdownlatch.html