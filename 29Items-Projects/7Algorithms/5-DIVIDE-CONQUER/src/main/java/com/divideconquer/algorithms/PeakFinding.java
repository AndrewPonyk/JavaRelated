package com.divideconquer.algorithms;

public final class PeakFinding {
    private PeakFinding() {
    }

    public static int peakIndex(int[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        return find(values, 0, values.length - 1);
    }

    private static int find(int[] values, int left, int right) {
        if (left == right) {
            return left;
        }
        int mid = left + (right - left) / 2;
        if (values[mid] < values[mid + 1]) {
            return find(values, mid + 1, right);
        }
        return find(values, left, mid);
    }
}
