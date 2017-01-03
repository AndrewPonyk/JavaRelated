package com.my;

import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class Part7ConsumerProducerUsingArrayBlockingQueue {
    private BlockingQueue<Integer> queue = new ArrayBlockingQueue(10);

    public static void main(String[] args) throws InterruptedException {
        Part7ConsumerProducerUsingArrayBlockingQueue app = new Part7ConsumerProducerUsingArrayBlockingQueue();

        Thread t1 = new Thread(()->{
            try {
                app.procuder();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        Thread t2 = new Thread(()->{
            try {
                app.consumer();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });


        t1.start();
        t2.start();
        t1.join();
        t2.join();
    }

    private void procuder() throws InterruptedException {
        Random random = new Random();

        while (true){
            Thread.sleep(55);
            queue.put(random.nextInt());
        }
    }

    private void consumer() throws InterruptedException {
        while (true){
            Thread.sleep(100);
            Integer value = queue.take();
            System.out.println("Taken value: " + value + "; Queue size is: " + queue.size());
        }
    }
}


// 1 2 3 4 5 6 7 8 9 9