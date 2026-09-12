package com.sortingsearching.sorting;

import java.util.Arrays;

public final class CountingSort {
    private CountingSort() {
    }

    public static int[] sort(int[] input) {
        if (input.length == 0) {
            return new int[0];
        }

        int min = Arrays.stream(input).min().orElse(0);
        int max = Arrays.stream(input).max().orElse(0);
        int[] counts = new int[max - min + 1];

        for (int value : input) {
            counts[value - min]++;
        }

        int[] result = new int[input.length];
        int index = 0;
        for (int offset = 0; offset < counts.length; offset++) {
            while (counts[offset]-- > 0) {
                result[index++] = offset + min;
            }
        }
        return result;
    }

    public static void main(String[] args) {
        int[] sample = {42, 7, 19, 3, 7, 99, 1};
        System.out.println("CountingSort");
        System.out.println("input : " + Arrays.toString(sample));
        System.out.println("output: " + Arrays.toString(sort(sample)));
    }
}
