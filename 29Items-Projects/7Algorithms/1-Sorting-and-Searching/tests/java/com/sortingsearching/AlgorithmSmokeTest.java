package com.sortingsearching;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.sortingsearching.searching.BinarySearch;
import com.sortingsearching.searching.BinarySearchTree;
import com.sortingsearching.searching.ExponentialSearch;
import com.sortingsearching.searching.HashSearch;
import com.sortingsearching.searching.InterpolationSearch;
import com.sortingsearching.searching.JumpSearch;
import com.sortingsearching.searching.MatrixSearch;
import com.sortingsearching.searching.RedBlackTree;
import com.sortingsearching.searching.TernarySearch;
import com.sortingsearching.sorting.BubbleSort;
import com.sortingsearching.sorting.BucketSort;
import com.sortingsearching.sorting.CountingSort;
import com.sortingsearching.sorting.HeapSort;
import com.sortingsearching.sorting.InsertionSort;
import com.sortingsearching.sorting.MergeSort;
import com.sortingsearching.sorting.QuickSort;
import com.sortingsearching.sorting.RadixSort;
import com.sortingsearching.sorting.SelectionSort;
import com.sortingsearching.sorting.ShellSort;

class AlgorithmSmokeTest {
    @Test
    void sortingAlgorithmsReturnSortedValues() {
        int[] values = {5, 1, 4, 2, 8, 0, 2};
        int[] expected = {0, 1, 2, 2, 4, 5, 8};

        assertArrayEquals(expected, QuickSort.sort(values));
        assertArrayEquals(expected, MergeSort.sort(values));
        assertArrayEquals(expected, HeapSort.sort(values));
        assertArrayEquals(expected, CountingSort.sort(values));
        assertArrayEquals(expected, BubbleSort.sort(values));
        assertArrayEquals(expected, InsertionSort.sort(values));
        assertArrayEquals(expected, RadixSort.sort(values));
        assertArrayEquals(expected, SelectionSort.sort(values));
        assertArrayEquals(expected, ShellSort.sort(values));
        assertArrayEquals(
                new double[] {0.0, 1.0, 2.0, 2.0, 4.0, 5.0, 8.0},
                BucketSort.sort(new double[] {5.0, 1.0, 4.0, 2.0, 8.0, 0.0, 2.0}, 10));
    }

    @Test
    void searchingAlgorithmsFindExpectedValues() {
        int[] values = {1, 2, 4, 5, 8};

        assertEquals(2, BinarySearch.search(values, 4));
        assertEquals(2, InterpolationSearch.search(values, 4));
        assertEquals(2, ExponentialSearch.search(values, 4));
        assertEquals(2, JumpSearch.search(values, 4));
        assertEquals(2, TernarySearch.search(values, 4));
        assertEquals(4, HashSearch.search(values, 8));
        assertEquals(-1, BinarySearch.search(values, 99));
    }

    @Test
    void matrixSearchVariantsReturnCoordinates() {
        int[][] rowColSorted = {
                {1, 4, 7},
                {2, 5, 8},
                {3, 6, 9}
        };
        int[][] flattenedSorted = {
                {1, 3, 5},
                {7, 9, 11}
        };

        assertArrayEquals(new int[] {2, 1}, MatrixSearch.staircaseSearch(rowColSorted, 6));
        assertArrayEquals(new int[] {1, 1}, MatrixSearch.flattenedBinarySearch(flattenedSorted, 9));
    }

    @Test
    void treeSearchAlgorithmsFindExpectedValues() {
        int[] values = {42, 7, 19, 3, 99, 1};
        BinarySearchTree bst = new BinarySearchTree();
        RedBlackTree redBlackTree = new RedBlackTree();

        for (int value : values) {
            bst.insert(value);
            redBlackTree.insert(value);
        }

        assertEquals(true, bst.contains(19));
        assertEquals(false, bst.contains(100));
        assertEquals(true, redBlackTree.contains(19));
        assertEquals(false, redBlackTree.contains(100));
    }
}
