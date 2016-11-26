package com.my.demo3;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by andrii on 22.11.16.
 */
public class AppSynchronizationKeyword {

    private int count = 0;
    private AtomicInteger atomicCount = new AtomicInteger(0);

    public static void main(String[] args) {
        System.out.println("Incrementing");

        AppSynchronizationKeyword app = new AppSynchronizationKeyword();
        app.doWork();
    }

    private void doWork() {
        Thread t1 = new Thread(new Runnable() {
            public void run() {
                for(int i =0;i<10000;i++){
                    count++;
                    atomicCount.incrementAndGet();
                }
            }
        });
        Thread t2 = new Thread(new Runnable() {
            public void run() {
                for (int i =0;i<10000;i++){
                    count++;
                    atomicCount.incrementAndGet();
                }
            }
        });

        t1.start();
        t2.start();


        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // we expect 20000
        System.out.println(count);
        System.out.println(atomicCount.get());
    }
}
