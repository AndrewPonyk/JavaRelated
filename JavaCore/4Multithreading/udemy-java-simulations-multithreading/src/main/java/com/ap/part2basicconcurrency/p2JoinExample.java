package com.ap.part2basicconcurrency;

/**
 * Created by andrii on 07.01.17.
 */
public class p2JoinExample implements Runnable{

    public static void main(String[] args) throws InterruptedException {
        Thread t1 = new Thread(new p2JoinExample());
        t1.start();
        //t1.join();
        System.out.println("Finished.");
    }

    public void run() {
        for (int i = 0; i < 10; i++) {
            System.out.println(i);
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
