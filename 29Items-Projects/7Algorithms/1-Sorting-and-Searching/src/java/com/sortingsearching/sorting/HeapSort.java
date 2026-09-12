package com.sortingsearching.sorting;

import java.util.Arrays;

public final class HeapSort {
    private HeapSort() {
    }

    public static int[] sort(int[] input) {
        int[] values = Arrays.copyOf(input, input.length);
        int n = values.length;

        for (int i = n / 2 - 1; i >= 0; i--) {
            heapify(values, n, i);
        }
        for (int end = n - 1; end > 0; end--) {
            swap(values, 0, end);
            heapify(values, end, 0);
        }
        return values;
    }

    private static void heapify(int[] values, int size, int root) {
        int largest = root;
        int left = 2 * root + 1;
        int right = 2 * root + 2;

        if (left < size && values[left] > values[largest]) {
            largest = left;
        }
        if (right < size && values[right] > values[largest]) {
            largest = right;
        }
        if (largest != root) {
            swap(values, root, largest);
            heapify(values, size, largest);
        }
    }

    private static void swap(int[] values, int i, int j) {
        int temp = values[i];
        values[i] = values[j];
        values[j] = temp;
    }

    public static void main(String[] args) {
        int[] sample = {42, 7, 19, 3, 7, 99, 1};
        System.out.println("HeapSort");
        System.out.println("input : " + Arrays.toString(sample));
        System.out.println("output: " + Arrays.toString(sort(sample)));
    }
}
