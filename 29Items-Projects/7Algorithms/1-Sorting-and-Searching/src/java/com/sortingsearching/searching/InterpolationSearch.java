package com.sortingsearching.searching;

import java.util.Arrays;

public final class InterpolationSearch {
    private InterpolationSearch() {
    }

    public static int search(int[] values, int target) {
        int low = 0;
        int high = values.length - 1;

        while (low <= high && values.length > 0 && values[low] <= target && target <= values[high]) {
            if (values[high] == values[low]) {
                return values[low] == target ? low : -1;
            }
            int position = low + ((target - values[low]) * (high - low)) / (values[high] - values[low]);
            if (values[position] == target) {
                return position;
            }
            if (values[position] < target) {
                low = position + 1;
            } else {
                high = position - 1;
            }
        }
        return -1;
    }

    public static void main(String[] args) {
        int[] sample = {1, 3, 7, 7, 19, 42, 99};
        int target = 19;
        System.out.println("Interpolation Search");
        System.out.println("input : " + Arrays.toString(sample));
        System.out.println("target: " + target);
        System.out.println("index : " + search(sample, target));
    }
}
