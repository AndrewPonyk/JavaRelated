package com.ap;

import java.util.Random;

public class OstapTask {
    public static void main(String[] args) {
        System.out.println("Hello, Ostap!");

        Random random = new Random();
        for (int i = 1; i <= 2000; i++) {
            int first = random.nextInt(0, 10);
            int second = random.nextInt(0, 10);

            if (first + second > 11) {
                continue;
            }
            String item = first + " + " + second + " =_____";
            System.out.println(item);
            if (i % 10 == 0) {
                System.out.println();
            }
        }
    }
}
