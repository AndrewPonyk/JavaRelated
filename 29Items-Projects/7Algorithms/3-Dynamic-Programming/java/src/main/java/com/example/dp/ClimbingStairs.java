package com.example.dp;

import java.util.HashMap;
import java.util.Map;

public final class ClimbingStairs {
    private ClimbingStairs() {
    }

    public static Result memoized(int steps) {
        return new Result("Climbing Stairs", "memoization", solve(steps, new HashMap<>()), Map.of());
    }

    private static int solve(int steps, Map<Integer, Integer> memo) {
        if (steps <= 1) {
            return 1;
        }
        if (memo.containsKey(steps)) {
            return memo.get(steps);
        }
        int value = solve(steps - 1, memo) + solve(steps - 2, memo);
        memo.put(steps, value);
        return value;
    }

    public static Result tabulated(int steps) {
        int[] dp = new int[steps + 1];
        dp[0] = 1;
        if (steps >= 1) {
            dp[1] = 1;
        }
        for (int i = 2; i <= steps; i++) {
            dp[i] = dp[i - 1] + dp[i - 2];
        }
        return new Result("Climbing Stairs", "tabulation", dp[steps], Map.of("steps", steps));
    }

    public static Result spaceOptimized(int steps) {
        int oneBack = 1;
        int twoBack = 1;
        for (int i = 2; i <= steps; i++) {
            int current = oneBack + twoBack;
            twoBack = oneBack;
            oneBack = current;
        }
        return new Result("Climbing Stairs", "space optimized", oneBack, Map.of());
    }
}
