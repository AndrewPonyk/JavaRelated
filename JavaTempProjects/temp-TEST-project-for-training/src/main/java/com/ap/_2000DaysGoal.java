package com.ap;

import java.time.LocalDate;

public class _2000DaysGoal {
    public static void main(String[] args) {
        System.out.printf("Hello, %n");
        final int total = 2000;
        LocalDate today = LocalDate.now();
        System.out.println("Today is: " + today);
        for (int i = 0; i < total; i++) {

            today = today.plusDays(1);

            System.out.println("[" + (total - i - 1) +"]" + today + "______________________________________________________" +
                    "_______________________________________________________");
        }
    }
}
