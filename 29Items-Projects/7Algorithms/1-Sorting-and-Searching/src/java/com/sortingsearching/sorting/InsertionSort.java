package com.sortingsearching.sorting;

import java.util.Arrays;

public final class InsertionSort {
    private InsertionSort() {
    }

    public static int[] sort(int[] input) {
        int[] values = Arrays.copyOf(input, input.length);
        for (int i = 1; i < values.length; i++) {
            int key = values[i];
            int j = i - 1;
            while (j >= 0 && values[j] > key) {
                values[j + 1] = values[j];
                j--;
            }
            values[j + 1] = key;
        }
        return values;
    }

    public static void main(String[] args) {
        int[] sample = {42, 7, 19, 3, 7, 99, 1};
        System.out.println("InsertionSort");
        System.out.println("input : " + Arrays.toString(sample));
        System.out.println("output: " + Arrays.toString(sort(sample)));
    }
}
