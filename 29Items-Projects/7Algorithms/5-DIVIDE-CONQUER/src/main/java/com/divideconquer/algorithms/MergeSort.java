package com.divideconquer.algorithms;

import java.util.Arrays;

public final class MergeSort {
    private MergeSort() {
    }

    public static int[] sort(int[] values) {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        if (values.length <= 1) {
            return Arrays.copyOf(values, values.length);
        }

        int mid = values.length / 2;
        int[] left = sort(Arrays.copyOfRange(values, 0, mid));
        int[] right = sort(Arrays.copyOfRange(values, mid, values.length));
        return merge(left, right);
    }

    private static int[] merge(int[] left, int[] right) {
        int[] result = new int[left.length + right.length];
        int leftIndex = 0;
        int rightIndex = 0;
        int resultIndex = 0;

        while (leftIndex < left.length && rightIndex < right.length) {
            if (left[leftIndex] <= right[rightIndex]) {
                result[resultIndex++] = left[leftIndex++];
            } else {
                result[resultIndex++] = right[rightIndex++];
            }
        }
        while (leftIndex < left.length) {
            result[resultIndex++] = left[leftIndex++];
        }
        while (rightIndex < right.length) {
            result[resultIndex++] = right[rightIndex++];
        }
        return result;
    }
}
