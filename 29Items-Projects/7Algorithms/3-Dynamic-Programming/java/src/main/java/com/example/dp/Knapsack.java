package com.example.dp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Knapsack {
    private Knapsack() {
    }

    public static Result memoized(int[] weights, int[] values, int capacity) {
        validate(weights, values, capacity);
        Map<String, Integer> memo = new HashMap<>();
        int best = solve(weights, values, 0, capacity, memo);
        return new Result("0/1 Knapsack", "memoization", best, Map.of());
    }

    private static int solve(int[] weights, int[] values, int index, int remaining, Map<String, Integer> memo) {
        if (index == weights.length || remaining == 0) {
            return 0;
        }
        String key = index + ":" + remaining;
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        int skip = solve(weights, values, index + 1, remaining, memo);
        int take = weights[index] <= remaining
                ? values[index] + solve(weights, values, index + 1, remaining - weights[index], memo)
                : 0;
        int best = Math.max(skip, take);
        memo.put(key, best);
        return best;
    }

    public static Result tabulated(int[] weights, int[] values, int capacity) {
        validate(weights, values, capacity);
        int[][] dp = new int[weights.length + 1][capacity + 1];
        for (int i = 1; i <= weights.length; i++) {
            for (int cap = 0; cap <= capacity; cap++) {
                dp[i][cap] = dp[i - 1][cap];
                if (weights[i - 1] <= cap) {
                    dp[i][cap] = Math.max(dp[i][cap], values[i - 1] + dp[i - 1][cap - weights[i - 1]]);
                }
            }
        }
        List<Integer> items = new ArrayList<>();
        int cap = capacity;
        for (int i = weights.length; i > 0; i--) {
            if (dp[i][cap] != dp[i - 1][cap]) {
                items.add(i - 1);
                cap -= weights[i - 1];
            }
        }
        Collections.reverse(items);
        return new Result("0/1 Knapsack", "tabulation", dp[weights.length][capacity], Map.of("items", items));
    }

    public static Result spaceOptimized(int[] weights, int[] values, int capacity) {
        validate(weights, values, capacity);
        int[] dp = new int[capacity + 1];
        for (int i = 0; i < weights.length; i++) {
            for (int cap = capacity; cap >= weights[i]; cap--) {
                dp[cap] = Math.max(dp[cap], values[i] + dp[cap - weights[i]]);
            }
        }
        return new Result("0/1 Knapsack", "space optimized", dp[capacity], Map.of());
    }

    private static void validate(int[] weights, int[] values, int capacity) {
        if (capacity < 0 || weights.length != values.length) {
            throw new IllegalArgumentException("invalid knapsack input");
        }
    }
}
