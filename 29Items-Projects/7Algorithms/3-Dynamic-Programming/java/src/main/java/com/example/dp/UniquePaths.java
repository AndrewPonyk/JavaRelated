package com.example.dp;

import java.util.HashMap;
import java.util.Map;

public final class UniquePaths {
    private UniquePaths() {
    }

    public static Result memoized(int rows, int cols) {
        return new Result("Unique Paths", "memoization", solve(0, 0, rows, cols, new HashMap<>()), Map.of());
    }

    private static int solve(int row, int col, int rows, int cols, Map<String, Integer> memo) {
        if (row == rows - 1 && col == cols - 1) {
            return 1;
        }
        if (row >= rows || col >= cols) {
            return 0;
        }
        String key = row + ":" + col;
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        int value = solve(row + 1, col, rows, cols, memo) + solve(row, col + 1, rows, cols, memo);
        memo.put(key, value);
        return value;
    }

    public static Result tabulated(int rows, int cols) {
        int[][] dp = new int[rows][cols];
        for (int row = 0; row < rows; row++) {
            dp[row][0] = 1;
        }
        for (int col = 0; col < cols; col++) {
            dp[0][col] = 1;
        }
        for (int row = 1; row < rows; row++) {
            for (int col = 1; col < cols; col++) {
                dp[row][col] = dp[row - 1][col] + dp[row][col - 1];
            }
        }
        return new Result("Unique Paths", "tabulation", dp[rows - 1][cols - 1], Map.of("rows", rows, "cols", cols));
    }

    public static Result spaceOptimized(int rows, int cols) {
        int[] dp = new int[cols];
        for (int col = 0; col < cols; col++) {
            dp[col] = 1;
        }
        for (int row = 1; row < rows; row++) {
            for (int col = 1; col < cols; col++) {
                dp[col] += dp[col - 1];
            }
        }
        return new Result("Unique Paths", "space optimized", dp[cols - 1], Map.of());
    }
}
