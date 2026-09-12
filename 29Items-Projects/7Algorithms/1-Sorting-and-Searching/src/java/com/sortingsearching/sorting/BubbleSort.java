package com.sortingsearching.sorting;

import java.util.Arrays;

public final class BubbleSort {
    private BubbleSort() {
    }

    public static int[] sort(int[] input) {
        int[] values = Arrays.copyOf(input, input.length);
        for (int i = 0; i < values.length; i++) {
            boolean swapped = false;
            for (int j = 0; j < values.length - i - 1; j++) {
                if (values[j] > values[j + 1]) {
                    int temp = values[j];
                    values[j] = values[j + 1];
                    values[j + 1] = temp;
                    swapped = true;
                }
            }
            if (!swapped) {
                break;
            }
        }
        return values;
    }

    public static void main(String[] args) {
        int[] sample = {42, 7, 19, 3, 7, 99, 1};
        System.out.println("BubbleSort");
        System.out.println("input : " + Arrays.toString(sample));
        System.out.println("output: " + Arrays.toString(sort(sample)));
    }
}
