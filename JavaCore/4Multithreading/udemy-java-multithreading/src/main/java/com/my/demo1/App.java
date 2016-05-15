package com.my.demo1;

/**
 * Created by andrii on 28.02.16.
 */

class Runner extends Thread {
    @Override
    public void run() {
        for (int i = 0; i < 10; i++){
            System.out.println("Hello " + i);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}

public class App {
    public static void main(String[] args) {
        Runner runner1 = new Runner();
        runner1.start();

        Runner runner2 = new Runner();
        runner1.start();
    }
}
