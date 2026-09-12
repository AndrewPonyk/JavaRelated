package com.divideconquer.algorithms;

public final class SelectionInTwoSortedArrays {
    private SelectionInTwoSortedArrays() {
    }

    public static int kthSmallest(int[] first, int[] second, int k) {
        if (first == null || second == null || first.length + second.length == 0) {
            throw new IllegalArgumentException("arrays must not both be empty");
        }
        if (k < 0 || k >= first.length + second.length) {
            throw new IllegalArgumentException("k must be inside combined bounds");
        }
        return kth(first, 0, second, 0, k + 1);
    }

    private static int kth(int[] first, int firstStart, int[] second, int secondStart, int k) {
        if (firstStart >= first.length) {
            return second[secondStart + k - 1];
        }
        if (secondStart >= second.length) {
            return first[firstStart + k - 1];
        }
        if (k == 1) {
            return Math.min(first[firstStart], second[secondStart]);
        }

        int half = k / 2;
        int firstKeyIndex = Math.min(firstStart + half, first.length) - 1;
        int secondKeyIndex = Math.min(secondStart + half, second.length) - 1;
        if (first[firstKeyIndex] <= second[secondKeyIndex]) {
            return kth(first, firstKeyIndex + 1, second, secondStart, k - (firstKeyIndex - firstStart + 1));
        }
        return kth(first, firstStart, second, secondKeyIndex + 1, k - (secondKeyIndex - secondStart + 1));
    }
}
