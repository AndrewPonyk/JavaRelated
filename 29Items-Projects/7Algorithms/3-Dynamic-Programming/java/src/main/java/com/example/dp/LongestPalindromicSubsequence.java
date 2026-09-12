package com.example.dp;

import java.util.HashMap;
import java.util.Map;

public final class LongestPalindromicSubsequence {
    private LongestPalindromicSubsequence() {
    }

    public static Result memoized(String text) {
        return new Result("Longest Palindromic Subsequence", "memoization", solve(text, 0, text.length() - 1, new HashMap<>()), Map.of());
    }

    private static int solve(String text, int left, int right, Map<String, Integer> memo) {
        if (left > right) {
            return 0;
        }
        if (left == right) {
            return 1;
        }
        String key = left + ":" + right;
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        int value = text.charAt(left) == text.charAt(right)
                ? 2 + solve(text, left + 1, right - 1, memo)
                : Math.max(solve(text, left + 1, right, memo), solve(text, left, right - 1, memo));
        memo.put(key, value);
        return value;
    }

    public static Result tabulated(String text) {
        int n = text.length();
        if (n == 0) {
            return new Result("Longest Palindromic Subsequence", "tabulation", 0, Map.of("sequence", ""));
        }
        int[][] dp = new int[n][n];
        for (int i = 0; i < n; i++) {
            dp[i][i] = 1;
        }
        for (int len = 2; len <= n; len++) {
            for (int left = 0; left <= n - len; left++) {
                int right = left + len - 1;
                if (text.charAt(left) == text.charAt(right)) {
                    dp[left][right] = 2 + (len > 2 ? dp[left + 1][right - 1] : 0);
                } else {
                    dp[left][right] = Math.max(dp[left + 1][right], dp[left][right - 1]);
                }
            }
        }
        return new Result("Longest Palindromic Subsequence", "tabulation", dp[0][n - 1], Map.of("length", dp[0][n - 1]));
    }

    public static Result spaceOptimized(String text) {
        int n = text.length();
        int[] dp = new int[n];
        for (int left = n - 1; left >= 0; left--) {
            int previousDiagonal = 0;
            dp[left] = 1;
            for (int right = left + 1; right < n; right++) {
                int saved = dp[right];
                if (text.charAt(left) == text.charAt(right)) {
                    dp[right] = 2 + previousDiagonal;
                } else {
                    dp[right] = Math.max(dp[right], dp[right - 1]);
                }
                previousDiagonal = saved;
            }
        }
        return new Result("Longest Palindromic Subsequence", "space optimized", n == 0 ? 0 : dp[n - 1], Map.of());
    }
}
