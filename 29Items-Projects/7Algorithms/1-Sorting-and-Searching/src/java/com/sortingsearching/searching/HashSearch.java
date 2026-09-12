package com.sortingsearching.searching;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class HashSearch {
    private HashSearch() {
    }

    public static int search(int[] values, int target) {
        Map<Integer, Integer> indexByValue = new HashMap<>();
        for (int i = 0; i < values.length; i++) {
            indexByValue.putIfAbsent(values[i], i);
        }
        return indexByValue.getOrDefault(target, -1);
    }

    public static void main(String[] args) {
        int[] sample = {42, 7, 19, 3, 7, 99, 1};
        int target = 19;
        System.out.println("Hash-based Search");
        System.out.println("input : " + Arrays.toString(sample));
        System.out.println("target: " + target);
        System.out.println("index : " + search(sample, target));
    }
}
