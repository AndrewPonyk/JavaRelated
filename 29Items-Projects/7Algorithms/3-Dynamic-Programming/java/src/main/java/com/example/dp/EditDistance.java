package com.example.dp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EditDistance {
    private EditDistance() {
    }

    public static Result memoized(String source, String target) {
        return new Result("Edit Distance", "memoization", solve(source, target, 0, 0, new HashMap<>()), Map.of());
    }

    private static int solve(String source, String target, int i, int j, Map<String, Integer> memo) {
        if (i == source.length()) {
            return target.length() - j;
        }
        if (j == target.length()) {
            return source.length() - i;
        }
        if (source.charAt(i) == target.charAt(j)) {
            return solve(source, target, i + 1, j + 1, memo);
        }
        String key = i + ":" + j;
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        int value = 1 + Math.min(
                solve(source, target, i + 1, j, memo),
                Math.min(solve(source, target, i, j + 1, memo), solve(source, target, i + 1, j + 1, memo)));
        memo.put(key, value);
        return value;
    }

    public static Result tabulated(String source, String target) {
        int[][] dp = buildTable(source, target);
        List<String> operations = new ArrayList<>();
        int i = source.length();
        int j = target.length();
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && source.charAt(i - 1) == target.charAt(j - 1)) {
                i--;
                j--;
            } else if (i > 0 && dp[i][j] == dp[i - 1][j] + 1) {
                operations.add("delete " + source.charAt(i - 1));
                i--;
            } else if (j > 0 && dp[i][j] == dp[i][j - 1] + 1) {
                operations.add("insert " + target.charAt(j - 1));
                j--;
            } else {
                operations.add("replace " + source.charAt(i - 1) + "->" + target.charAt(j - 1));
                i--;
                j--;
            }
        }
        Collections.reverse(operations);
        return new Result("Edit Distance", "tabulation", dp[source.length()][target.length()], Map.of("operations", operations));
    }

    private static int[][] buildTable(String source, String target) {
        int[][] dp = new int[source.length() + 1][target.length() + 1];
        for (int i = 0; i <= source.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= target.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= source.length(); i++) {
            for (int j = 1; j <= target.length(); j++) {
                dp[i][j] = source.charAt(i - 1) == target.charAt(j - 1)
                        ? dp[i - 1][j - 1]
                        : 1 + Math.min(dp[i - 1][j], Math.min(dp[i][j - 1], dp[i - 1][j - 1]));
            }
        }
        return dp;
    }

    public static Result spaceOptimized(String source, String target) {
        int[] previous = new int[target.length() + 1];
        for (int j = 0; j <= target.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= source.length(); i++) {
            int[] current = new int[target.length() + 1];
            current[0] = i;
            for (int j = 1; j <= target.length(); j++) {
                current[j] = source.charAt(i - 1) == target.charAt(j - 1)
                        ? previous[j - 1]
                        : 1 + Math.min(previous[j], Math.min(current[j - 1], previous[j - 1]));
            }
            previous = current;
        }
        return new Result("Edit Distance", "space optimized", previous[target.length()], Map.of());
    }
}
