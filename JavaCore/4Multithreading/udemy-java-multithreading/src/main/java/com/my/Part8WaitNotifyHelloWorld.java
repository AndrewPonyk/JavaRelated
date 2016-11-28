package com.my;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Created by andrii on 27.11.16.
 */
public class Part8WaitNotifyHelloWorld {
    private static BlockingQueue queue = new ArrayBlockingQueue(10);

    public static void main(String[] args) throws InterruptedException {
        queue.put(10);queue.put(10);queue.put(10);queue.put(10);queue.put(10);queue.put(10);queue.put(10);queue.put(10);queue.put(10);queue.put(10);

        queue.put(10);
    }
}
