package com.example.dp;

import java.util.HashMap;
import java.util.Map;

public final class LongestCommonSubsequence {
    private LongestCommonSubsequence() {
    }

    public static Result memoized(String a, String b) {
        int value = solve(a, b, 0, 0, new HashMap<>());
        return new Result("LCS", "memoization", value, Map.of());
    }

    private static int solve(String a, String b, int i, int j, Map<String, Integer> memo) {
        if (i == a.length() || j == b.length()) {
            return 0;
        }
        String key = i + ":" + j;
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        int value = a.charAt(i) == b.charAt(j)
                ? 1 + solve(a, b, i + 1, j + 1, memo)
                : Math.max(solve(a, b, i + 1, j, memo), solve(a, b, i, j + 1, memo));
        memo.put(key, value);
        return value;
    }

    public static Result tabulated(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                dp[i][j] = a.charAt(i - 1) == b.charAt(j - 1)
                        ? 1 + dp[i - 1][j - 1]
                        : Math.max(dp[i - 1][j], dp[i][j - 1]);
            }
        }
        StringBuilder sequence = new StringBuilder();
        int i = a.length();
        int j = b.length();
        while (i > 0 && j > 0) {
            if (a.charAt(i - 1) == b.charAt(j - 1)) {
                sequence.append(a.charAt(i - 1));
                i--;
                j--;
            } else if (dp[i - 1][j] >= dp[i][j - 1]) {
                i--;
            } else {
                j--;
            }
        }
        return new Result("LCS", "tabulation", dp[a.length()][b.length()], Map.of("sequence", sequence.reverse().toString()));
    }

    public static Result spaceOptimized(String a, String b) {
        int[] previous = new int[b.length() + 1];
        for (int i = 1; i <= a.length(); i++) {
            int[] current = new int[b.length() + 1];
            for (int j = 1; j <= b.length(); j++) {
                current[j] = a.charAt(i - 1) == b.charAt(j - 1)
                        ? 1 + previous[j - 1]
                        : Math.max(previous[j], current[j - 1]);
            }
            previous = current;
        }
        return new Result("LCS", "space optimized", previous[b.length()], Map.of());
    }
}
