package com.example.dp;

import java.util.HashMap;
import java.util.Map;

public final class MatrixChainMultiplication {
    private MatrixChainMultiplication() {
    }

    public static Result memoized(int[] dimensions) {
        int value = solve(dimensions, 1, dimensions.length - 1, new HashMap<>());
        return new Result("Matrix Chain", "memoization", value, Map.of());
    }

    private static int solve(int[] dimensions, int i, int j, Map<String, Integer> memo) {
        if (i == j) {
            return 0;
        }
        String key = i + ":" + j;
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        int best = Integer.MAX_VALUE;
        for (int k = i; k < j; k++) {
            int cost = solve(dimensions, i, k, memo)
                    + solve(dimensions, k + 1, j, memo)
                    + dimensions[i - 1] * dimensions[k] * dimensions[j];
            best = Math.min(best, cost);
        }
        memo.put(key, best);
        return best;
    }

    public static Result tabulated(int[] dimensions) {
        int n = dimensions.length - 1;
        int[][] dp = new int[n + 1][n + 1];
        int[][] split = new int[n + 1][n + 1];
        for (int len = 2; len <= n; len++) {
            for (int i = 1; i <= n - len + 1; i++) {
                int j = i + len - 1;
                dp[i][j] = Integer.MAX_VALUE;
                for (int k = i; k < j; k++) {
                    int cost = dp[i][k] + dp[k + 1][j] + dimensions[i - 1] * dimensions[k] * dimensions[j];
                    if (cost < dp[i][j]) {
                        dp[i][j] = cost;
                        split[i][j] = k;
                    }
                }
            }
        }
        return new Result("Matrix Chain", "tabulation", dp[1][n], Map.of("parenthesization", build(split, 1, n)));
    }

    private static String build(int[][] split, int i, int j) {
        if (i == j) {
            return "A" + i;
        }
        int k = split[i][j];
        return "(" + build(split, i, k) + " x " + build(split, k + 1, j) + ")";
    }

    public static Result spaceOptimized(int[] dimensions) {
        Result result = memoized(dimensions);
        return new Result("Matrix Chain", "compact memoization", result.value(), Map.of());
    }
}
