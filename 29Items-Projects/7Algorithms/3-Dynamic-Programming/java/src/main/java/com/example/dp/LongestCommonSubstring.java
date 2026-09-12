package com.example.dp;

import java.util.HashMap;
import java.util.Map;

public final class LongestCommonSubstring {
    private LongestCommonSubstring() {
    }

    public static Result memoized(String a, String b) {
        Map<String, Integer> memo = new HashMap<>();
        int bestLength = 0;
        int bestStart = 0;
        for (int i = 0; i < a.length(); i++) {
            for (int j = 0; j < b.length(); j++) {
                int length = suffix(a, b, i, j, memo);
                if (length > bestLength) {
                    bestLength = length;
                    bestStart = i;
                }
            }
        }
        return new Result("Longest Common Substring", "memoization", bestLength, Map.of("substring", a.substring(bestStart, bestStart + bestLength)));
    }

    private static int suffix(String a, String b, int i, int j, Map<String, Integer> memo) {
        if (i == a.length() || j == b.length() || a.charAt(i) != b.charAt(j)) {
            return 0;
        }
        String key = i + ":" + j;
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        int value = 1 + suffix(a, b, i + 1, j + 1, memo);
        memo.put(key, value);
        return value;
    }

    public static Result tabulated(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        int bestLength = 0;
        int end = 0;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = 1 + dp[i - 1][j - 1];
                    if (dp[i][j] > bestLength) {
                        bestLength = dp[i][j];
                        end = i;
                    }
                }
            }
        }
        return new Result("Longest Common Substring", "tabulation", bestLength, Map.of("substring", a.substring(end - bestLength, end)));
    }

    public static Result spaceOptimized(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int bestLength = 0;
        int end = 0;
        for (int i = 1; i <= a.length(); i++) {
            int[] current = new int[b.length() + 1];
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    current[j] = 1 + previous[j - 1];
                    if (current[j] > bestLength) {
                        bestLength = current[j];
                        end = i;
                    }
                }
            }
            previous = current;
        }
        return new Result("Longest Common Substring", "space optimized", bestLength, Map.of("substring", a.substring(end - bestLength, end)));
    }
}
