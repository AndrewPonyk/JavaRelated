package com.sortingsearching.sorting;

import java.util.Arrays;

public final class SelectionSort {
    private SelectionSort() {
    }

    public static int[] sort(int[] input) {
        int[] values = Arrays.copyOf(input, input.length);
        for (int i = 0; i < values.length; i++) {
            int minIndex = i;
            for (int j = i + 1; j < values.length; j++) {
                if (values[j] < values[minIndex]) {
                    minIndex = j;
                }
            }
            int temp = values[i];
            values[i] = values[minIndex];
            values[minIndex] = temp;
        }
        return values;
    }

    public static void main(String[] args) {
        int[] sample = {42, 7, 19, 3, 7, 99, 1};
        System.out.println("SelectionSort");
        System.out.println("input : " + Arrays.toString(sample));
        System.out.println("output: " + Arrays.toString(sort(sample)));
    }
}
