package com.my;

import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

public class Part11DeadLock {
    Account acc1 = new Account();
    Account acc2 = new Account();

    private ReentrantLock lock1 = new ReentrantLock();
    private ReentrantLock lock2 = new ReentrantLock();

    public void firstThread(){
        Random random = new Random();
        for (int i = 0; i < 1000; i++) {
            lock2.lock();
            lock1.lock();
            acc1.transfer(acc1, acc2, random.nextInt(10));
            lock1.unlock();
            lock2.unlock();
        }
    }

    public void secondThread(){
        Random random = new Random();
        for (int i = 0; i < 1000; i++) {
            lock1.lock();
            lock2.lock();
            acc2.transfer(acc2, acc1, random.nextInt(10));
            lock1.unlock();
            lock2.unlock();
        }
    }

    public void finished(){
        System.out.println("Account 1 balance:" + acc1.getBalance());
        System.out.println("Account 2 balance:" + acc2.getBalance());
        System.out.println("Total balance: " + (acc1.getBalance() + acc2.getBalance()));
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Starting threads, which will cause deadlock");
        Part11DeadLock runner = new Part11DeadLock();

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

class Account{
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

    public static void transfer(Account acc1, Account acc2, int amount){
        acc1.withdraw(amount);
        acc2.deposit(amount);
    }
}