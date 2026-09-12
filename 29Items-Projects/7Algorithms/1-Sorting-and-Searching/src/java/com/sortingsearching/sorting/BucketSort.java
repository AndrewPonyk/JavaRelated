package com.sortingsearching.sorting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Arrays;

public final class BucketSort {
    private BucketSort() {
    }

    public static double[] sort(double[] input, int bucketCount) {
        if (input.length == 0) {
            return new double[0];
        }
        if (bucketCount <= 0) {
            throw new IllegalArgumentException("bucketCount must be positive");
        }

        double min = input[0];
        double max = input[0];
        for (double value : input) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        if (min == max) {
            return Arrays.copyOf(input, input.length);
        }

        List<List<Double>> buckets = new ArrayList<>();
        for (int i = 0; i < bucketCount; i++) {
            buckets.add(new ArrayList<>());
        }

        double range = max - min;
        for (double value : input) {
            int index = (int) (((value - min) / range) * (bucketCount - 1));
            buckets.get(index).add(value);
        }

        double[] result = new double[input.length];
        int index = 0;
        for (List<Double> bucket : buckets) {
            Collections.sort(bucket);
            for (double value : bucket) {
                result[index++] = value;
            }
        }
        return result;
    }

    public static void main(String[] args) {
        double[] sample = {0.42, 0.07, 0.19, 0.03, 0.07, 0.99, 0.01};
        System.out.println("BucketSort");
        System.out.println("input : " + Arrays.toString(sample));
        System.out.println("output: " + Arrays.toString(sort(sample, 10)));
    }
}
