package com.divideconquer.algorithms;

public final class BinarySearch {
    private BinarySearch() {
    }

    public static int indexOf(int[] sortedValues, int target) {
        if (sortedValues == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        return search(sortedValues, target, 0, sortedValues.length - 1);
    }

    private static int search(int[] values, int target, int left, int right) {
        if (left > right) {
            return -1;
        }

        int mid = left + (right - left) / 2;
        if (values[mid] == target) {
            return mid;
        }
        if (target < values[mid]) {
            return search(values, target, left, mid - 1);
        }
        return search(values, target, mid + 1, right);
    }
}
