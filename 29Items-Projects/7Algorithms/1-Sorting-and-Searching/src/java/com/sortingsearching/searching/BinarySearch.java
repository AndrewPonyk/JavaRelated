package com.sortingsearching.searching;

import java.util.Arrays;

public final class BinarySearch {
    private BinarySearch() {
    }

    public static int search(int[] values, int target) {
        int left = 0;
        int right = values.length - 1;
        while (left <= right) {
            int mid = left + (right - left) / 2;
            if (values[mid] == target) {
                return mid;
            }
            if (values[mid] < target) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        return -1;
    }

    public static void main(String[] args) {
        int[] sample = {1, 3, 7, 7, 19, 42, 99};
        int target = 19;
        System.out.println("Binary Search");
        System.out.println("input : " + Arrays.toString(sample));
        System.out.println("target: " + target);
        System.out.println("index : " + search(sample, target));
    }
}
