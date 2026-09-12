package com.divideconquer.algorithms;

import java.util.Arrays;

public final class DivideConquerDpOptimization {
    private static final long INF = Long.MAX_VALUE / 4;

    private DivideConquerDpOptimization() {
    }

    public static long minSquaredPartitionCost(int[] values, int groups) {
        if (values == null || values.length == 0 || groups <= 0) {
            throw new IllegalArgumentException("values must not be empty and groups must be positive");
        }
        int n = values.length;
        long[] prefix = new long[n + 1];
        for (int i = 0; i < n; i++) {
            prefix[i + 1] = prefix[i] + values[i];
        }

        long[] previous = new long[n + 1];
        Arrays.fill(previous, INF);
        previous[0] = 0;
        for (int group = 1; group <= groups; group++) {
            long[] current = new long[n + 1];
            Arrays.fill(current, INF);
            compute(current, previous, prefix, 1, n, 0, n - 1);
            previous = current;
        }
        return previous[n];
    }

    private static void compute(long[] current, long[] previous, long[] prefix, int left, int right, int optLeft, int optRight) {
        if (left > right) {
            return;
        }
        int mid = left + (right - left) / 2;
        int bestSplit = optLeft;
        long bestCost = INF;
        int upper = Math.min(mid - 1, optRight);
        for (int split = optLeft; split <= upper; split++) {
            long candidate = previous[split] + cost(prefix, split, mid);
            if (candidate < bestCost) {
                bestCost = candidate;
                bestSplit = split;
            }
        }
        current[mid] = bestCost;
        compute(current, previous, prefix, left, mid - 1, optLeft, bestSplit);
        compute(current, previous, prefix, mid + 1, right, bestSplit, optRight);
    }

    private static long cost(long[] prefix, int start, int end) {
        long sum = prefix[end] - prefix[start];
        return sum * sum;
    }
}
