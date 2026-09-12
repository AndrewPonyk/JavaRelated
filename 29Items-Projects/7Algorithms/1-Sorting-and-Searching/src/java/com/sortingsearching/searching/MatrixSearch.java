package com.sortingsearching.searching;

import java.util.Arrays;

public final class MatrixSearch {
    private MatrixSearch() {
    }

    public static int[] staircaseSearch(int[][] matrix, int target) {
        if (matrix.length == 0 || matrix[0].length == 0) {
            return null;
        }
        int row = 0;
        int col = matrix[0].length - 1;
        while (row < matrix.length && col >= 0) {
            int value = matrix[row][col];
            if (value == target) {
                return new int[] {row, col};
            }
            if (value > target) {
                col--;
            } else {
                row++;
            }
        }
        return null;
    }

    public static int[] flattenedBinarySearch(int[][] matrix, int target) {
        if (matrix.length == 0 || matrix[0].length == 0) {
            return null;
        }
        int rows = matrix.length;
        int cols = matrix[0].length;
        int left = 0;
        int right = rows * cols - 1;

        while (left <= right) {
            int mid = left + (right - left) / 2;
            int row = mid / cols;
            int col = mid % cols;
            int value = matrix[row][col];
            if (value == target) {
                return new int[] {row, col};
            }
            if (value < target) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        return null;
    }

    public static void main(String[] args) {
        int[][] rowColSorted = {
                {1, 4, 7, 11},
                {2, 5, 8, 12},
                {3, 6, 9, 16}
        };
        int[][] flattenedSorted = {
                {1, 3, 5},
                {7, 9, 11}
        };

        System.out.println("2D Matrix Search");
        System.out.println("staircase target: 9");
        System.out.println("staircase output: " + Arrays.toString(staircaseSearch(rowColSorted, 9)));
        System.out.println("flattened target: 9");
        System.out.println("flattened output: " + Arrays.toString(flattenedBinarySearch(flattenedSorted, 9)));
    }
}
