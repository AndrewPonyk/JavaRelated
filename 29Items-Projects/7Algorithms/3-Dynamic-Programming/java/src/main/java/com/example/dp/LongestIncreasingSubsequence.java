package com.example.dp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LongestIncreasingSubsequence {
    private LongestIncreasingSubsequence() {
    }

    public static Result memoized(int[] nums) {
        return new Result("LIS", "memoization", solve(nums, 0, -1, new HashMap<>()), Map.of());
    }

    private static int solve(int[] nums, int index, int previous, Map<String, Integer> memo) {
        if (index == nums.length) {
            return 0;
        }
        String key = index + ":" + previous;
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        int skip = solve(nums, index + 1, previous, memo);
        int take = previous == -1 || nums[index] > nums[previous]
                ? 1 + solve(nums, index + 1, index, memo)
                : 0;
        int value = Math.max(skip, take);
        memo.put(key, value);
        return value;
    }

    public static Result tabulated(int[] nums) {
        if (nums.length == 0) {
            return new Result("LIS", "tabulation", 0, Map.of("sequence", List.of()));
        }
        int[] dp = new int[nums.length];
        int[] parent = new int[nums.length];
        Arrays.fill(dp, 1);
        Arrays.fill(parent, -1);
        for (int i = 0; i < nums.length; i++) {
            for (int j = 0; j < i; j++) {
                if (nums[j] < nums[i] && dp[j] + 1 > dp[i]) {
                    dp[i] = dp[j] + 1;
                    parent[i] = j;
                }
            }
        }
        int best = 0;
        for (int i = 1; i < dp.length; i++) {
            if (dp[i] > dp[best]) {
                best = i;
            }
        }
        List<Integer> sequence = new ArrayList<>();
        for (int at = best; at != -1; at = parent[at]) {
            sequence.add(nums[at]);
        }
        Collections.reverse(sequence);
        return new Result("LIS", "tabulation", sequence.size(), Map.of("sequence", sequence));
    }

    public static Result spaceOptimized(int[] nums) {
        List<Integer> tails = new ArrayList<>();
        for (int num : nums) {
            int pos = Collections.binarySearch(tails, num);
            if (pos < 0) {
                pos = -pos - 1;
            }
            if (pos == tails.size()) {
                tails.add(num);
            } else {
                tails.set(pos, num);
            }
        }
        return new Result("LIS", "patience/space optimized", tails.size(), Map.of());
    }
}
