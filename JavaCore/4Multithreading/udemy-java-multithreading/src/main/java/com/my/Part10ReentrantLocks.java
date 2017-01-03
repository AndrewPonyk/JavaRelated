package com.my;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by andrii on 27.11.16.
 */
public class Part10ReentrantLocks {
    private int count = 0;
    private Lock lock = new ReentrantLock();

    public void firstThread() {
        System.out.println("Is locked in first thread" + lock.tryLock());
        lock.lock();

        try {
            increment();
        } finally {
            lock.unlock();
        }
    }

    public void secondThread() {
        lock.lock();
        try {
            increment();
        } finally {
            lock.unlock();
        }


    }

    private void increment() {
        for (int i = 0; i < 10000; i++) {
            count++;
        }
    }

    public void finished() {
        System.out.println("Count is :" + count);
    }

    public static void main(String[] args) throws InterruptedException {
        Part10ReentrantLocks app = new Part10ReentrantLocks();

        Thread t1 = new Thread(() -> {
            app.firstThread();
        });
        Thread t2 = new Thread(() -> {
            app.secondThread();
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        app.finished();
    }
}