package com.example.dp;

import java.util.HashMap;
import java.util.Map;

public final class EggDrop {
    private EggDrop() {
    }

    public static Result memoized(int eggs, int floors) {
        return new Result("Egg Drop", "memoization", solve(eggs, floors, new HashMap<>()), Map.of());
    }

    private static int solve(int eggs, int floors, Map<String, Integer> memo) {
        if (floors <= 1 || eggs == 1) {
            return floors;
        }
        String key = eggs + ":" + floors;
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        int best = Integer.MAX_VALUE;
        for (int floor = 1; floor <= floors; floor++) {
            best = Math.min(best, 1 + Math.max(solve(eggs - 1, floor - 1, memo), solve(eggs, floors - floor, memo)));
        }
        memo.put(key, best);
        return best;
    }

    public static Result tabulated(int eggs, int floors) {
        int[][] dp = new int[eggs + 1][floors + 1];
        for (int egg = 1; egg <= eggs; egg++) {
            for (int floor = 1; floor <= floors; floor++) {
                dp[egg][floor] = floor;
            }
        }
        for (int egg = 2; egg <= eggs; egg++) {
            for (int floor = 2; floor <= floors; floor++) {
                for (int drop = 1; drop <= floor; drop++) {
                    dp[egg][floor] = Math.min(dp[egg][floor], 1 + Math.max(dp[egg - 1][drop - 1], dp[egg][floor - drop]));
                }
            }
        }
        return new Result("Egg Drop", "tabulation", dp[eggs][floors], Map.of());
    }

    public static Result spaceOptimized(int eggs, int floors) {
        int moves = 0;
        int[] reachable = new int[eggs + 1];
        while (reachable[eggs] < floors) {
            moves++;
            for (int egg = eggs; egg >= 1; egg--) {
                reachable[egg] = reachable[egg] + reachable[egg - 1] + 1;
            }
        }
        return new Result("Egg Drop", "moves/space optimized", moves, Map.of("reachable_floors", reachable[eggs]));
    }
}
