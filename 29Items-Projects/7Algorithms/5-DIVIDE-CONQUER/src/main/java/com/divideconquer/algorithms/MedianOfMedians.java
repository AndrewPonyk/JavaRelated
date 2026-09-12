package com.divideconquer.algorithms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MedianOfMedians {
    private MedianOfMedians() {
    }

    public static int kthSmallest(List<Integer> values, int k) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        if (values.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("values must not contain null values");
        }
        if (k < 0 || k >= values.size()) {
            throw new IllegalArgumentException("k must be inside the list bounds");
        }
        return select(new ArrayList<>(values), k);
    }

    private static int select(List<Integer> values, int k) {
        if (values.size() <= 5) {
            Collections.sort(values);
            return values.get(k);
        }

        List<Integer> medians = new ArrayList<>();
        for (int i = 0; i < values.size(); i += 5) {
            List<Integer> group = new ArrayList<>(values.subList(i, Math.min(i + 5, values.size())));
            Collections.sort(group);
            medians.add(group.get(group.size() / 2));
        }

        int pivot = select(medians, medians.size() / 2);
        List<Integer> lows = new ArrayList<>();
        List<Integer> highs = new ArrayList<>();
        List<Integer> equals = new ArrayList<>();

        for (int value : values) {
            if (value < pivot) {
                lows.add(value);
            } else if (value > pivot) {
                highs.add(value);
            } else {
                equals.add(value);
            }
        }

        if (k < lows.size()) {
            return select(lows, k);
        }
        if (k < lows.size() + equals.size()) {
            return pivot;
        }
        return select(highs, k - lows.size() - equals.size());
    }
}
