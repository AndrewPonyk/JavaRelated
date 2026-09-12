package com.example.dp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TravelingSalesman {
    private static final int INF = 1_000_000_000;

    private TravelingSalesman() {
    }

    public static Result memoized(int[][] dist) {
        int value = solve(dist, 0, 1, new HashMap<>());
        return new Result("Traveling Salesman", "memoization/bitmask", value, Map.of());
    }

    private static int solve(int[][] dist, int city, int mask, Map<String, Integer> memo) {
        int n = dist.length;
        if (mask == (1 << n) - 1) {
            return dist[city][0];
        }
        String key = city + ":" + mask;
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        int best = INF;
        for (int next = 0; next < n; next++) {
            if ((mask & (1 << next)) == 0) {
                best = Math.min(best, dist[city][next] + solve(dist, next, mask | (1 << next), memo));
            }
        }
        memo.put(key, best);
        return best;
    }

    public static Result tabulated(int[][] dist) {
        int n = dist.length;
        int full = 1 << n;
        int[][] dp = new int[full][n];
        int[][] parent = new int[full][n];
        for (int mask = 0; mask < full; mask++) {
            for (int city = 0; city < n; city++) {
                dp[mask][city] = INF;
                parent[mask][city] = -1;
            }
        }
        dp[1][0] = 0;
        for (int mask = 0; mask < full; mask++) {
            for (int city = 0; city < n; city++) {
                if (dp[mask][city] >= INF) {
                    continue;
                }
                for (int next = 0; next < n; next++) {
                    if ((mask & (1 << next)) != 0) {
                        continue;
                    }
                    int nextMask = mask | (1 << next);
                    int cost = dp[mask][city] + dist[city][next];
                    if (cost < dp[nextMask][next]) {
                        dp[nextMask][next] = cost;
                        parent[nextMask][next] = city;
                    }
                }
            }
        }
        int bestCity = 1;
        int bestCost = INF;
        for (int city = 1; city < n; city++) {
            int cost = dp[full - 1][city] + dist[city][0];
            if (cost < bestCost) {
                bestCost = cost;
                bestCity = city;
            }
        }
        List<Integer> reverse = new ArrayList<>();
        int mask = full - 1;
        int city = bestCity;
        while (city != -1) {
            reverse.add(city);
            int previous = parent[mask][city];
            mask ^= 1 << city;
            city = previous;
        }
        Collections.reverse(reverse);
        List<Integer> tour = new ArrayList<>(reverse);
        tour.add(0);
        return new Result("Traveling Salesman", "tabulation/bitmask", bestCost, Map.of("tour", tour));
    }

    public static Result spaceOptimized(int[][] dist) {
        Result result = memoized(dist);
        return new Result("Traveling Salesman", "compact bitmask memoization", result.value(), Map.of());
    }
}
