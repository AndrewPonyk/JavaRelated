package com.sortingsearching.searching;

import java.util.Arrays;

public final class TernarySearch {
    private TernarySearch() {
    }

    public static int search(int[] values, int target) {
        int left = 0;
        int right = values.length - 1;
        while (left <= right) {
            int third = (right - left) / 3;
            int mid1 = left + third;
            int mid2 = right - third;
            if (values[mid1] == target) {
                return mid1;
            }
            if (values[mid2] == target) {
                return mid2;
            }
            if (target < values[mid1]) {
                right = mid1 - 1;
            } else if (target > values[mid2]) {
                left = mid2 + 1;
            } else {
                left = mid1 + 1;
                right = mid2 - 1;
            }
        }
        return -1;
    }

    public static void main(String[] args) {
        int[] sample = {1, 3, 7, 7, 19, 42, 99};
        int target = 19;
        System.out.println("Ternary Search");
        System.out.println("input : " + Arrays.toString(sample));
        System.out.println("target: " + target);
        System.out.println("index : " + search(sample, target));
    }
}
