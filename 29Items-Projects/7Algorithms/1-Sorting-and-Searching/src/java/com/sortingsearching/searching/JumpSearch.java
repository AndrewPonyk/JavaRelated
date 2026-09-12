package com.sortingsearching.searching;

import java.util.Arrays;

public final class JumpSearch {
    private JumpSearch() {
    }

    public static int search(int[] values, int target) {
        int n = values.length;
        if (n == 0) {
            return -1;
        }
        int stepSize = Math.max(1, (int) Math.sqrt(n));
        int step = stepSize;
        int previous = 0;

        while (previous < n && values[Math.min(step, n) - 1] < target) {
            previous = step;
            step += stepSize;
            if (previous >= n) {
                return -1;
            }
        }
        for (int i = previous; i < Math.min(step, n); i++) {
            if (values[i] == target) {
                return i;
            }
        }
        return -1;
    }

    public static void main(String[] args) {
        int[] sample = {1, 3, 7, 7, 19, 42, 99};
        int target = 19;
        System.out.println("Jump Search");
        System.out.println("input : " + Arrays.toString(sample));
        System.out.println("target: " + target);
        System.out.println("index : " + search(sample, target));
    }
}
