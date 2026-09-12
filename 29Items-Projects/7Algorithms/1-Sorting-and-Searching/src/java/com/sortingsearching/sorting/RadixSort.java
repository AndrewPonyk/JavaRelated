package com.sortingsearching.sorting;

import java.util.Arrays;

public final class RadixSort {
    private RadixSort() {
    }

    public static int[] sort(int[] input) {
        int[] values = Arrays.copyOf(input, input.length);
        for (int value : values) {
            if (value < 0) {
                throw new IllegalArgumentException("RadixSort supports non-negative integers only");
            }
        }
        if (values.length == 0) {
            return values;
        }

        int max = Arrays.stream(values).max().orElse(0);
        for (int exponent = 1; max / exponent > 0; exponent *= 10) {
            countingByDigit(values, exponent);
        }
        return values;
    }

    private static void countingByDigit(int[] values, int exponent) {
        int[] output = new int[values.length];
        int[] counts = new int[10];

        for (int value : values) {
            counts[(value / exponent) % 10]++;
        }
        for (int i = 1; i < 10; i++) {
            counts[i] += counts[i - 1];
        }
        for (int i = values.length - 1; i >= 0; i--) {
            int digit = (values[i] / exponent) % 10;
            output[counts[digit] - 1] = values[i];
            counts[digit]--;
        }
        System.arraycopy(output, 0, values, 0, values.length);
    }

    public static void main(String[] args) {
        int[] sample = {42, 7, 19, 3, 7, 99, 1};
        System.out.println("RadixSort");
        System.out.println("input : " + Arrays.toString(sample));
        System.out.println("output: " + Arrays.toString(sort(sample)));
    }
}
