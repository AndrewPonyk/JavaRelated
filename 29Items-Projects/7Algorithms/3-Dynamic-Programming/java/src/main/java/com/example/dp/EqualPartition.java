package com.example.dp;

import java.util.Map;

public final class EqualPartition {
    private EqualPartition() {
    }

    public static Result memoized(int[] nums) {
        int total = sum(nums);
        boolean value = total % 2 == 0 && Boolean.TRUE.equals(SubsetSum.memoized(nums, total / 2).value());
        return new Result("Equal Partition", "memoization", value, Map.of());
    }

    public static Result tabulated(int[] nums) {
        int total = sum(nums);
        if (total % 2 != 0) {
            return new Result("Equal Partition", "tabulation", false, Map.of("target", "none"));
        }
        Result result = SubsetSum.tabulated(nums, total / 2);
        return new Result("Equal Partition", "tabulation", result.value(), Map.of("target", total / 2, "subset", result.details().get("subset")));
    }

    public static Result spaceOptimized(int[] nums) {
        int total = sum(nums);
        boolean value = total % 2 == 0 && Boolean.TRUE.equals(SubsetSum.spaceOptimized(nums, total / 2).value());
        return new Result("Equal Partition", "space optimized", value, Map.of());
    }

    private static int sum(int[] nums) {
        int total = 0;
        for (int num : nums) {
            total += num;
        }
        return total;
    }
}
