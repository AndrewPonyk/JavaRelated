package com.divideconquer.algorithms;

import java.util.Arrays;

public final class CountingInversions {
    private CountingInversions() {
    }

    public static long count(int[] values) {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        int[] copy = Arrays.copyOf(values, values.length);
        int[] buffer = new int[copy.length];
        return countAndSort(copy, buffer, 0, copy.length);
    }

    private static long countAndSort(int[] values, int[] buffer, int start, int end) {
        if (end - start <= 1) {
            return 0;
        }

        int mid = start + (end - start) / 2;
        long inversions = countAndSort(values, buffer, start, mid);
        inversions += countAndSort(values, buffer, mid, end);
        inversions += mergeAndCount(values, buffer, start, mid, end);
        return inversions;
    }

    private static long mergeAndCount(int[] values, int[] buffer, int start, int mid, int end) {
        int left = start;
        int right = mid;
        int index = start;
        long inversions = 0;

        while (left < mid && right < end) {
            if (values[left] <= values[right]) {
                buffer[index++] = values[left++];
            } else {
                buffer[index++] = values[right++];
                inversions += mid - left;
            }
        }
        while (left < mid) {
            buffer[index++] = values[left++];
        }
        while (right < end) {
            buffer[index++] = values[right++];
        }
        System.arraycopy(buffer, start, values, start, end - start);
        return inversions;
    }
}
