package com.example.dp;

import java.util.HashMap;
import java.util.Map;

public final class OptimalBinarySearchTree {
    private OptimalBinarySearchTree() {
    }

    public static Result memoized(String[] keys, int[] freq) {
        int[] prefix = prefix(freq);
        int value = solve(0, keys.length - 1, prefix, new HashMap<>());
        return new Result("Optimal BST", "memoization", value, Map.of());
    }

    private static int solve(int i, int j, int[] prefix, Map<String, Integer> memo) {
        if (i > j) {
            return 0;
        }
        String key = i + ":" + j;
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        int total = prefix[j + 1] - prefix[i];
        int best = Integer.MAX_VALUE;
        for (int root = i; root <= j; root++) {
            best = Math.min(best, solve(i, root - 1, prefix, memo) + solve(root + 1, j, prefix, memo) + total);
        }
        memo.put(key, best);
        return best;
    }

    public static Result tabulated(String[] keys, int[] freq) {
        int n = keys.length;
        int[] prefix = prefix(freq);
        int[][] dp = new int[n][n];
        int[][] rootAt = new int[n][n];
        for (int i = 0; i < n; i++) {
            dp[i][i] = freq[i];
            rootAt[i][i] = i;
        }
        for (int len = 2; len <= n; len++) {
            for (int i = 0; i <= n - len; i++) {
                int j = i + len - 1;
                int total = prefix[j + 1] - prefix[i];
                dp[i][j] = Integer.MAX_VALUE;
                for (int root = i; root <= j; root++) {
                    int left = root > i ? dp[i][root - 1] : 0;
                    int right = root < j ? dp[root + 1][j] : 0;
                    if (left + right + total < dp[i][j]) {
                        dp[i][j] = left + right + total;
                        rootAt[i][j] = root;
                    }
                }
            }
        }
        return new Result("Optimal BST", "tabulation", dp[0][n - 1], Map.of("tree", build(keys, rootAt, 0, n - 1)));
    }

    private static int[] prefix(int[] freq) {
        int[] prefix = new int[freq.length + 1];
        for (int i = 0; i < freq.length; i++) {
            prefix[i + 1] = prefix[i] + freq[i];
        }
        return prefix;
    }

    private static String build(String[] keys, int[][] rootAt, int i, int j) {
        if (i > j) {
            return ".";
        }
        int root = rootAt[i][j];
        return keys[root] + "(" + build(keys, rootAt, i, root - 1) + "," + build(keys, rootAt, root + 1, j) + ")";
    }

    public static Result spaceOptimized(String[] keys, int[] freq) {
        Result result = memoized(keys, freq);
        return new Result("Optimal BST", "compact memoization", result.value(), Map.of());
    }
}
