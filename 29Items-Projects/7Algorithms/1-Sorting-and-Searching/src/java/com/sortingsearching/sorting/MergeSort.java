package com.sortingsearching.sorting;

import java.util.Arrays;

public final class MergeSort {
    private MergeSort() {
    }

    public static int[] sort(int[] input) {
        int[] values = Arrays.copyOf(input, input.length);
        mergeSort(values, 0, values.length - 1);
        return values;
    }

    private static void mergeSort(int[] values, int left, int right) {
        if (left >= right) {
            return;
        }
        int mid = left + (right - left) / 2;
        mergeSort(values, left, mid);
        mergeSort(values, mid + 1, right);
        merge(values, left, mid, right);
    }

    private static void merge(int[] values, int left, int mid, int right) {
        int[] leftValues = Arrays.copyOfRange(values, left, mid + 1);
        int[] rightValues = Arrays.copyOfRange(values, mid + 1, right + 1);
        int i = 0;
        int j = 0;
        int k = left;

        while (i < leftValues.length && j < rightValues.length) {
            values[k++] = leftValues[i] <= rightValues[j] ? leftValues[i++] : rightValues[j++];
        }
        while (i < leftValues.length) {
            values[k++] = leftValues[i++];
        }
        while (j < rightValues.length) {
            values[k++] = rightValues[j++];
        }
    }

    public static void main(String[] args) {
        int[] sample = {42, 7, 19, 3, 7, 99, 1};
        System.out.println("MergeSort");
        System.out.println("input : " + Arrays.toString(sample));
        System.out.println("output: " + Arrays.toString(sort(sample)));
    }
}
