package com.sortingsearching.searching;

import java.util.Arrays;

public final class ExponentialSearch {
    private ExponentialSearch() {
    }

    public static int search(int[] values, int target) {
        if (values.length == 0) {
            return -1;
        }
        if (values[0] == target) {
            return 0;
        }
        int bound = 1;
        while (bound < values.length && values[bound] < target) {
            bound *= 2;
        }
        int offset = bound / 2;
        int[] range = Arrays.copyOfRange(values, offset, Math.min(bound + 1, values.length));
        int localIndex = BinarySearch.search(range, target);
        return localIndex == -1 ? -1 : offset + localIndex;
    }

    public static void main(String[] args) {
        int[] sample = {1, 3, 7, 7, 19, 42, 99};
        int target = 19;
        System.out.println("Exponential Search");
        System.out.println("input : " + Arrays.toString(sample));
        System.out.println("target: " + target);
        System.out.println("index : " + search(sample, target));
    }
}
