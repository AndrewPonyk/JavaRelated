package com.my;

import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Created by andrii on 27.11.16.
 */
public class Part8WaitNotifyHelloWorld {
    private static BlockingQueue queue = new ArrayBlockingQueue(10);

    public void consume(){
        Scanner scanner = new Scanner(System.in);
        synchronized (this){
            System.out.println("Type text line");
            scanner.nextLine();
            notify();
        }
    }

    public void produce() throws InterruptedException {
        synchronized (this){
            System.out.println("Producer thread running...");
            wait();
            System.out.println("Producer resumed...");
        }


    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("wait and notify");
        Part8WaitNotifyHelloWorld app = new Part8WaitNotifyHelloWorld();

        Thread t1 = new Thread(() -> {
            try {
                app.produce();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        Thread t2 = new Thread(() -> {
            app.consume();
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        new Object().wait();
    }
}
