package com.example.dp;

import java.util.HashMap;
import java.util.Map;

public final class Fibonacci {
    private Fibonacci() {
    }

    public static Result memoized(int n) {
        return new Result("Fibonacci", "memoization", solve(n, new HashMap<>()), Map.of());
    }

    private static int solve(int n, Map<Integer, Integer> memo) {
        if (n <= 1) {
            return n;
        }
        if (memo.containsKey(n)) {
            return memo.get(n);
        }
        int value = solve(n - 1, memo) + solve(n - 2, memo);
        memo.put(n, value);
        return value;
    }

    public static Result tabulated(int n) {
        if (n <= 1) {
            return new Result("Fibonacci", "tabulation", n, Map.of());
        }
        int[] dp = new int[n + 1];
        dp[1] = 1;
        for (int i = 2; i <= n; i++) {
            dp[i] = dp[i - 1] + dp[i - 2];
        }
        return new Result("Fibonacci", "tabulation", dp[n], Map.of("sequence_length", dp.length));
    }

    public static Result spaceOptimized(int n) {
        int previous = 0;
        int current = 1;
        for (int i = 0; i < n; i++) {
            int next = previous + current;
            previous = current;
            current = next;
        }
        return new Result("Fibonacci", "space optimized", previous, Map.of());
    }
}
