package com.sortingsearching.sorting;

import java.util.Arrays;

public final class QuickSort {
    private QuickSort() {
    }

    public static int[] sort(int[] input) {
        int[] values = Arrays.copyOf(input, input.length);
        quickSort(values, 0, values.length - 1);
        return values;
    }

    private static void quickSort(int[] values, int low, int high) {
        if (low >= high) {
            return;
        }
        int pivotIndex = partition(values, low, high);
        quickSort(values, low, pivotIndex - 1);
        quickSort(values, pivotIndex + 1, high);
    }

    private static int partition(int[] values, int low, int high) {
        int pivot = values[high];
        int i = low - 1;
        for (int j = low; j < high; j++) {
            if (values[j] <= pivot) {
                i++;
                swap(values, i, j);
            }
        }
        swap(values, i + 1, high);
        return i + 1;
    }

    private static void swap(int[] values, int i, int j) {
        int temp = values[i];
        values[i] = values[j];
        values[j] = temp;
    }

    public static void main(String[] args) {
        int[] sample = {42, 7, 19, 3, 7, 99, 1};
        System.out.println("QuickSort");
        System.out.println("input : " + Arrays.toString(sample));
        System.out.println("output: " + Arrays.toString(sort(sample)));
    }
}
