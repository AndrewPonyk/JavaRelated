package com.my.part1StartThreadsWays;


public class ExtendsThread extends Thread {
    @Override
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
        ExtendsThread t1 = new ExtendsThread();
        t1.start();

        ExtendsThread t2 = new ExtendsThread();
        t2.start();
    }
}
