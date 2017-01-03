package com.my;

import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Part11DeadLockRESOLUTIONALGORITHM {
    Account1 acc1 = new Account1();
    Account1 acc2 = new Account1();

    private Lock lock1 = new ReentrantLock();
    private Lock lock2 = new ReentrantLock();

    private void acquireLocks(Lock firstLock, Lock secondLock){
        while (true){
            boolean gotFirstLock = false;
            boolean gotSecondLock = false;

            try {
                gotFirstLock = firstLock.tryLock();
                gotSecondLock = secondLock.tryLock();
            } finally {
                if(gotFirstLock && gotSecondLock){
                    return;
                }

                if(gotFirstLock){
                    firstLock.unlock();
                }

                if(gotSecondLock){
                    secondLock.unlock();
                }
            }

        }
    }

    public void firstThread(){
        Random random = new Random();
        for (int i = 0; i < 1000; i++) {
            acquireLocks(lock2, lock1);
            try{
                acc1.transfer(acc1, acc2, random.nextInt(10));
            }finally {
                lock1.unlock();
                lock2.unlock();
            }

        }
    }

    public void secondThread(){
        Random random = new Random();
        for (int i = 0; i < 1000; i++) {
            acquireLocks(lock1, lock2);
            try{
                acc2.transfer(acc2, acc1, random.nextInt(10));
            }finally {
                lock1.unlock();
                lock2.unlock();
            }
        }
    }

    public void finished(){
        System.out.println("Account 1 balance:" + acc1.getBalance());
        System.out.println("Account 2 balance:" + acc2.getBalance());
        System.out.println("Total balance: " + (acc1.getBalance() + acc2.getBalance()));
    }

    public static void main(String[] args) throws InterruptedException {
    	System.out.println("Starting threads, which will cause deadlock");
        Part11DeadLockRESOLUTIONALGORITHM runner = new Part11DeadLockRESOLUTIONALGORITHM();

        Thread t1 = new Thread(() -> {
            runner.firstThread();
        });

        Thread t2 = new Thread(() -> {
            runner.secondThread();
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        runner.finished();

    }
}

class Account1{
    private int balance = 10000;

    public void deposit(int amount){
        balance+= amount;
    }

    public void withdraw(int amount){
        balance-= amount;
    }

    public int getBalance(){
        return balance;
    }

    public static void transfer(Account1 acc1, Account1 acc2, int amount){
        acc1.withdraw(amount);
        acc2.deposit(amount);
    }
}