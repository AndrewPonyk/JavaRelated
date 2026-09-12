package com.example.dp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MinimumPathSum {
    private static final int INF = 1_000_000_000;

    private MinimumPathSum() {
    }

    public static Result memoized(int[][] grid) {
        return new Result("Minimum Path Sum", "memoization", solve(grid, 0, 0, new HashMap<>()), Map.of());
    }

    private static int solve(int[][] grid, int row, int col, Map<String, Integer> memo) {
        if (row >= grid.length || col >= grid[0].length) {
            return INF;
        }
        if (row == grid.length - 1 && col == grid[0].length - 1) {
            return grid[row][col];
        }
        String key = row + ":" + col;
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        int value = grid[row][col] + Math.min(solve(grid, row + 1, col, memo), solve(grid, row, col + 1, memo));
        memo.put(key, value);
        return value;
    }

    public static Result tabulated(int[][] grid) {
        int rows = grid.length;
        int cols = grid[0].length;
        int[][] dp = new int[rows][cols];
        String[][] parent = new String[rows][cols];
        dp[0][0] = grid[0][0];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (row == 0 && col == 0) {
                    continue;
                }
                int top = row > 0 ? dp[row - 1][col] : INF;
                int left = col > 0 ? dp[row][col - 1] : INF;
                if (top <= left) {
                    dp[row][col] = grid[row][col] + top;
                    parent[row][col] = (row - 1) + "," + col;
                } else {
                    dp[row][col] = grid[row][col] + left;
                    parent[row][col] = row + "," + (col - 1);
                }
            }
        }
        List<String> path = new ArrayList<>();
        int row = rows - 1;
        int col = cols - 1;
        while (row != 0 || col != 0) {
            path.add(row + "," + col);
            String[] parts = parent[row][col].split(",");
            row = Integer.parseInt(parts[0]);
            col = Integer.parseInt(parts[1]);
        }
        path.add("0,0");
        Collections.reverse(path);
        return new Result("Minimum Path Sum", "tabulation", dp[rows - 1][cols - 1], Map.of("path", path));
    }

    public static Result spaceOptimized(int[][] grid) {
        int[] dp = new int[grid[0].length];
        dp[0] = 0;
        for (int col = 1; col < dp.length; col++) {
            dp[col] = INF;
        }
        for (int[] row : grid) {
            dp[0] += row[0];
            for (int col = 1; col < row.length; col++) {
                dp[col] = row[col] + Math.min(dp[col], dp[col - 1]);
            }
        }
        return new Result("Minimum Path Sum", "space optimized", dp[dp.length - 1], Map.of());
    }
}
