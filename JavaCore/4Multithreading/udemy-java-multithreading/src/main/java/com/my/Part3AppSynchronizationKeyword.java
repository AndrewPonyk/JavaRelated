package com.my;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by andrii on 22.11.16.
 */
public class Part3AppSynchronizationKeyword {

    private int count = 0; // making volatile DOESN'T help !!! , we should
    private AtomicInteger atomicCount = new AtomicInteger(0);

    public static void main(String[] args) {
        System.out.println("Incrementing");

        Part3AppSynchronizationKeyword app = new Part3AppSynchronizationKeyword();
        app.doWork();
    }

    private void doWork() {
        Thread t1 = new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < 10000; i++) {
                    count++;
                    atomicCount.incrementAndGet();
                }
            }
        });
        Thread t2 = new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < 10000; i++) {
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

        // we also can use synchronized method incrementCount(){ count++}, but Atomic integer is better))
    }
}
