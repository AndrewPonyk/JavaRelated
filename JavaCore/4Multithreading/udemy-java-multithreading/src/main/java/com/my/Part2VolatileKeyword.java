package com.my;

import java.util.Scanner;

public class Part2VolatileKeyword extends Thread{

    private volatile boolean running = true; // !!! Must be volatile, to ensure what each thread have it is own running variable (and not cached copy)

    @Override
    public void run() {
        while (running){
            System.out.println("Running");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        Part2VolatileKeyword proc1 = new Part2VolatileKeyword();
        proc1.start();

        Scanner scanner = new Scanner(System.in);
        scanner.nextLine();
        proc1.shutdown();
    }

    private void shutdown() {
        running = false;
    }
}
