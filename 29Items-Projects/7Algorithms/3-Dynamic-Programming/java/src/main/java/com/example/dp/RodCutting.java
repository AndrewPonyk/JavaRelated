package com.example.dp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RodCutting {
    private RodCutting() {
    }

    public static Result memoized(int[] prices, int length) {
        return new Result("Rod Cutting", "memoization", solve(prices, length, new HashMap<>()), Map.of());
    }

    private static int solve(int[] prices, int remaining, Map<Integer, Integer> memo) {
        if (remaining == 0) {
            return 0;
        }
        if (memo.containsKey(remaining)) {
            return memo.get(remaining);
        }
        int best = 0;
        for (int cut = 1; cut <= remaining; cut++) {
            best = Math.max(best, prices[cut - 1] + solve(prices, remaining - cut, memo));
        }
        memo.put(remaining, best);
        return best;
    }

    public static Result tabulated(int[] prices, int length) {
        int[] dp = new int[length + 1];
        int[] parent = new int[length + 1];
        for (int size = 1; size <= length; size++) {
            for (int cut = 1; cut <= size; cut++) {
                int value = prices[cut - 1] + dp[size - cut];
                if (value > dp[size]) {
                    dp[size] = value;
                    parent[size] = cut;
                }
            }
        }
        List<Integer> cuts = new ArrayList<>();
        for (int size = length; size > 0; size -= parent[size]) {
            cuts.add(parent[size]);
        }
        return new Result("Rod Cutting", "tabulation", dp[length], Map.of("cuts", cuts));
    }

    public static Result spaceOptimized(int[] prices, int length) {
        Result result = tabulated(prices, length);
        return new Result("Rod Cutting", "space optimized", result.value(), result.details());
    }
}
