package com.my;

/**
 * Created by andrii on 27.11.16.
 */
public class Part14InterruptingThreads {
    public static void main(String[] args) throws InterruptedException {

        Thread thread = new Thread(() -> {
            for (int i = 0; i < 1E7; i++) {
                if(Thread.currentThread().isInterrupted()){
                    System.out.println("I was interrupted at " + i + " iteration");
                    break;
                }
                Math.sin(i);
                System.out.println(i);

            }
        });
        thread.start();

        Thread.sleep(500);

        thread.interrupt();

    }
}
