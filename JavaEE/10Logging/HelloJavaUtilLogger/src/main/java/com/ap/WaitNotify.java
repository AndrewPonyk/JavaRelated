package com.ap;
import java.io.IOException;

public class WaitNotify {

    private  Object lock = new Object();

    private void method1(){

        synchronized (lock){
            try {
                this.wait();
                System.out.println("Wait released!");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void method2(){
        synchronized (lock){
            try {
                System.in.read();
                lock.notify();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("Wait notify example!");
        final WaitNotify app = new WaitNotify();

        new Thread(new Runnable(){
            public void run() {
                app.method1();
            }
        }).start();

        new Thread(new Runnable(){
            public void run() {
                app.method2();
            }
        }).start();

    }
}