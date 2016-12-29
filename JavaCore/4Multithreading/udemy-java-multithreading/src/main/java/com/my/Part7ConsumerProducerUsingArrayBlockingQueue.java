package com.my;

import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Created by andrii on 27.11.16.
 */
public class Part7ConsumerProducerUsingArrayBlockingQueue {
    private BlockingQueue queue = new ArrayBlockingQueue(10);

    public static void main(String[] args) {
        Part7ConsumerProducerUsingArrayBlockingQueue app = new Part7ConsumerProducerUsingArrayBlockingQueue();

    }

    private void procuder() throws InterruptedException {
        Random random = new Random();

        while (true){
            Thread.sleep(50);
            queue.put(random.nextInt());
        }
    }

    private void consumer() throws InterruptedException {
        while (true){
            Thread.sleep(100);
            Object value = queue.take();
        }
    }
}
