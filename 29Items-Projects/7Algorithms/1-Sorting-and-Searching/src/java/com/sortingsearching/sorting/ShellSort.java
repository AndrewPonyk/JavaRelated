package com.sortingsearching.sorting;

import java.util.Arrays;

public final class ShellSort {
    private ShellSort() {
    }

    public static int[] sort(int[] input) {
        int[] values = Arrays.copyOf(input, input.length);
        for (int gap = values.length / 2; gap > 0; gap /= 2) {
            for (int i = gap; i < values.length; i++) {
                int current = values[i];
                int j = i;
                while (j >= gap && values[j - gap] > current) {
                    values[j] = values[j - gap];
                    j -= gap;
                }
                values[j] = current;
            }
        }
        return values;
    }

    public static void main(String[] args) {
        int[] sample = {42, 7, 19, 3, 7, 99, 1};
        System.out.println("ShellSort");
        System.out.println("input : " + Arrays.toString(sample));
        System.out.println("output: " + Arrays.toString(sort(sample)));
    }
}
