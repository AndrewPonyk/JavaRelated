package com.divideconquer.algorithms;

public final class RotatedArraySearch {
    private RotatedArraySearch() {
    }

    public static int indexOf(int[] values, int target) {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        return search(values, target, 0, values.length - 1);
    }

    private static int search(int[] values, int target, int left, int right) {
        if (left > right) {
            return -1;
        }
        int mid = left + (right - left) / 2;
        if (values[mid] == target) {
            return mid;
        }
        if (values[left] <= values[mid]) {
            if (target >= values[left] && target < values[mid]) {
                return search(values, target, left, mid - 1);
            }
            return search(values, target, mid + 1, right);
        }
        if (target > values[mid] && target <= values[right]) {
            return search(values, target, mid + 1, right);
        }
        return search(values, target, left, mid - 1);
    }
}
