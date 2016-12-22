package com.my;

import java.util.LinkedList;
import java.util.Random;

/**
 * Created by andrii on 27.11.16.
 */
public class Part9ConsumerProducerUsingLowLevel {
    private LinkedList<Integer> list = new LinkedList<>();
    private final int LIMIT = 10;
    private Object lock = new Object();

    public void produce() throws InterruptedException {
        int value = 0;
        while (true){
            synchronized (lock){
                while (list.size() == LIMIT){
                    lock.wait();
                }
                System.out.println("Adding to list value: " + value);
                list.add(value++);

                lock.notify();
            }
            Random r = new Random();
            Thread.sleep(r.nextInt(100));
        }
    }

    public void consume() throws InterruptedException {
        Random r = new Random();
        while (true){
            synchronized (lock){
                while (list.size()==0){
                    lock.wait();
                }

                System.out.print("List size is " + list.size());
                int value = list.removeFirst();
                System.out.println("; value is :" + value);
                lock.notify();
            }

            Thread.sleep(r.nextInt(1000));
        }
    }

    public static void main(String[] args) {
        Part9ConsumerProducerUsingLowLevel part9ConsumerProducerUsingLowLevel = new Part9ConsumerProducerUsingLowLevel();
        Thread t1 = new Thread(()->{
            try {
                part9ConsumerProducerUsingLowLevel.produce();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        Thread t2 = new Thread(()->{
            try {
                part9ConsumerProducerUsingLowLevel.consume();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        t1.start();
        t2.start();

    }
}
