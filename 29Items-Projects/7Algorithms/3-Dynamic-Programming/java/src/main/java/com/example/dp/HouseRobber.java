package com.example.dp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HouseRobber {
    private HouseRobber() {
    }

    public static Result memoized(int[] houses) {
        return new Result("House Robber", "memoization", solve(houses, 0, new HashMap<>()), Map.of());
    }

    private static int solve(int[] houses, int index, Map<Integer, Integer> memo) {
        if (index >= houses.length) {
            return 0;
        }
        if (memo.containsKey(index)) {
            return memo.get(index);
        }
        int value = Math.max(solve(houses, index + 1, memo), houses[index] + solve(houses, index + 2, memo));
        memo.put(index, value);
        return value;
    }

    public static Result tabulated(int[] houses) {
        int[] dp = new int[houses.length + 2];
        for (int i = houses.length - 1; i >= 0; i--) {
            dp[i] = Math.max(dp[i + 1], houses[i] + dp[i + 2]);
        }
        List<Integer> chosen = new ArrayList<>();
        int i = 0;
        while (i < houses.length) {
            if (houses[i] + dp[i + 2] >= dp[i + 1]) {
                chosen.add(i);
                i += 2;
            } else {
                i++;
            }
        }
        return new Result("House Robber", "tabulation", dp[0], Map.of("houses", chosen));
    }

    public static Result spaceOptimized(int[] houses) {
        int nextOne = 0;
        int nextTwo = 0;
        for (int i = houses.length - 1; i >= 0; i--) {
            int current = Math.max(nextOne, houses[i] + nextTwo);
            nextTwo = nextOne;
            nextOne = current;
        }
        return new Result("House Robber", "space optimized", nextOne, Map.of());
    }
}
