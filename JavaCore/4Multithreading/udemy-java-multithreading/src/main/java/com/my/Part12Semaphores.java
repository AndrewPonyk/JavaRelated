package com.my;


import java.util.concurrent.Semaphore;

public class Part12Semaphores {
    public static void main(String[] args) {
        Semaphore sem = new Semaphore(2);

        new Thread(()->{
            try {
                sem.acquire();
                System.out.println("Started: " + Thread.currentThread().getName());
                Thread.sleep(3000);
                System.out.println("Finishe: " + Thread.currentThread().getName());
                sem.release();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        new Thread(()->{
            try {
                sem.acquire();
                System.out.println("Started: " + Thread.currentThread().getName());
                Thread.sleep(3000);
                System.out.println("Finishe: " + Thread.currentThread().getName());
                sem.release();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        new Thread(()->{
            try {
                sem.acquire();
                System.out.println("Started: " + Thread.currentThread().getName());
                sem.acquire();
                Thread.sleep(3000);
                System.out.println("Finishe: " + Thread.currentThread().getName());
                sem.release();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        new Thread(()->{
            try {
                sem.acquire();
                System.out.println("Started: " + Thread.currentThread().getName());
                Thread.sleep(3000);
                System.out.println("Finishe: " + Thread.currentThread().getName());
                sem.release();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
