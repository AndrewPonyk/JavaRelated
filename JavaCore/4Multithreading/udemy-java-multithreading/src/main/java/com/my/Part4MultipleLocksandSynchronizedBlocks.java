package com.my;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class Part4MultipleLocksandSynchronizedBlocks{
    private Random random = new Random();
    private List<Integer> list1 = new ArrayList<>();
    private List<Integer> list2 = new ArrayList<>();

    private Object lock1 = new Object();
    private Object lock2 = new Object();

    public void stage1(){
        synchronized (lock1){
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            list1.add(random.nextInt());
        }
    }

    public void stage2(){
        synchronized (lock2){
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            list2.add(random.nextInt());
        }
    }

    public void process(){
        for (int i = 0; i < 1000; i++) {
            stage1();
            stage2();
        }
    }

    public  void doWork() {


        long start = System.currentTimeMillis();

        Thread t1 = new Thread(()->{
            process();
        });
        Thread t2 = new Thread(()->{
            process();
        });
        t1.start();
        t2.start();

        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        long end = System.currentTimeMillis();

        System.out.println("Time take :" + (end - start));
        System.out.println("List1 size: " + list1.size() + "; List2 size:" + list2.size());
    }


    public static void main(String[] args) {
        new Part4MultipleLocksandSynchronizedBlocks().doWork();
    }
}