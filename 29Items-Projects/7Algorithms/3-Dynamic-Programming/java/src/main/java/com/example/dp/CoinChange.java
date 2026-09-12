package com.example.dp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CoinChange {
    private static final int INF = 1_000_000_000;

    private CoinChange() {
    }

    public static Result memoized(int[] coins, int amount) {
        int value = solve(coins, amount, new HashMap<>());
        return new Result("Coin Change", "memoization", value >= INF ? -1 : value, Map.of());
    }

    private static int solve(int[] coins, int amount, Map<Integer, Integer> memo) {
        if (amount == 0) {
            return 0;
        }
        if (amount < 0) {
            return INF;
        }
        if (memo.containsKey(amount)) {
            return memo.get(amount);
        }
        int best = INF;
        for (int coin : coins) {
            best = Math.min(best, 1 + solve(coins, amount - coin, memo));
        }
        memo.put(amount, best);
        return best;
    }

    public static Result tabulated(int[] coins, int amount) {
        int[] dp = new int[amount + 1];
        int[] parent = new int[amount + 1];
        Arrays.fill(dp, INF);
        Arrays.fill(parent, -1);
        dp[0] = 0;
        for (int current = 1; current <= amount; current++) {
            for (int coin : coins) {
                if (coin <= current && dp[current - coin] + 1 < dp[current]) {
                    dp[current] = dp[current - coin] + 1;
                    parent[current] = coin;
                }
            }
        }
        List<Integer> chosen = new ArrayList<>();
        for (int current = amount; current > 0 && parent[current] != -1; current -= parent[current]) {
            chosen.add(parent[current]);
        }
        int value = dp[amount] >= INF ? -1 : dp[amount];
        return new Result("Coin Change", "tabulation", value, Map.of("coins", value == -1 ? List.of() : chosen));
    }

    public static Result spaceOptimized(int[] coins, int amount) {
        Result result = tabulated(coins, amount);
        return new Result("Coin Change", "space optimized", result.value(), result.details());
    }
}
