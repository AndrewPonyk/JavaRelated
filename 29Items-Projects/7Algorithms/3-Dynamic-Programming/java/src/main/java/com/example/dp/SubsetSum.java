package com.example.dp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SubsetSum {
    private SubsetSum() {
    }

    public static Result memoized(int[] nums, int target) {
        return new Result("Subset Sum", "memoization", solve(nums, 0, target, new HashMap<>()), Map.of());
    }

    private static boolean solve(int[] nums, int index, int remaining, Map<String, Boolean> memo) {
        if (remaining == 0) {
            return true;
        }
        if (index == nums.length || remaining < 0) {
            return false;
        }
        String key = index + ":" + remaining;
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        boolean value = solve(nums, index + 1, remaining, memo) || solve(nums, index + 1, remaining - nums[index], memo);
        memo.put(key, value);
        return value;
    }

    public static Result tabulated(int[] nums, int target) {
        boolean[][] dp = new boolean[nums.length + 1][target + 1];
        dp[0][0] = true;
        for (int i = 1; i <= nums.length; i++) {
            for (int total = 0; total <= target; total++) {
                dp[i][total] = dp[i - 1][total] || (total >= nums[i - 1] && dp[i - 1][total - nums[i - 1]]);
            }
        }
        List<Integer> subset = new ArrayList<>();
        int i = nums.length;
        int total = target;
        while (i > 0 && total > 0 && dp[i][total]) {
            if (dp[i - 1][total]) {
                i--;
            } else {
                subset.add(nums[i - 1]);
                total -= nums[i - 1];
                i--;
            }
        }
        Collections.reverse(subset);
        return new Result("Subset Sum", "tabulation", dp[nums.length][target], Map.of("subset", dp[nums.length][target] ? subset : List.of()));
    }

    public static Result spaceOptimized(int[] nums, int target) {
        boolean[] dp = new boolean[target + 1];
        dp[0] = true;
        for (int num : nums) {
            for (int total = target; total >= num; total--) {
                dp[total] = dp[total] || dp[total - num];
            }
        }
        return new Result("Subset Sum", "space optimized", dp[target], Map.of());
    }
}
