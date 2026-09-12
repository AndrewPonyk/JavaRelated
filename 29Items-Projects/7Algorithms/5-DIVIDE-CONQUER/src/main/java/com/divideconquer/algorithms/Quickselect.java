package com.divideconquer.algorithms;

import java.util.Arrays;

public final class Quickselect {
    private Quickselect() {
    }

    public static int kthSmallest(int[] values, int k) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        if (k < 0 || k >= values.length) {
            throw new IllegalArgumentException("k must be inside the array bounds");
        }

        int[] copy = Arrays.copyOf(values, values.length);
        int left = 0;
        int right = copy.length - 1;

        while (left <= right) {
            int pivotIndex = partition(copy, left, right);
            if (pivotIndex == k) {
                return copy[pivotIndex];
            }
            if (pivotIndex < k) {
                left = pivotIndex + 1;
            } else {
                right = pivotIndex - 1;
            }
        }
        throw new IllegalStateException("selection failed");
    }

    private static int partition(int[] values, int left, int right) {
        int pivot = values[right];
        int store = left;
        for (int i = left; i < right; i++) {
            if (values[i] <= pivot) {
                swap(values, store, i);
                store++;
            }
        }
        swap(values, store, right);
        return store;
    }

    private static void swap(int[] values, int i, int j) {
        int temp = values[i];
        values[i] = values[j];
        values[j] = temp;
    }
}
