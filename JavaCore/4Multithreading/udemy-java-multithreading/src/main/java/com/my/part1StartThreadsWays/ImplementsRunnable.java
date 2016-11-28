package com.my.part1StartThreadsWays;

public class ImplementsRunnable implements Runnable {

    public void run() {
        for (int i = 0; i < 10; i++) {
            System.out.println("Hello :" + i);
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        Thread t1 = new Thread(new ImplementsRunnable());
        t1.start();

        Thread t2 = new Thread(new ImplementsRunnable());
        t2.start();
    }
}
