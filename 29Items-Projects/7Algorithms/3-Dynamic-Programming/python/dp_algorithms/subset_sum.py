from __future__ import annotations

from functools import lru_cache

from dp_algorithms.common import DPResult, require_non_negative


def memoized(nums: list[int], target: int) -> DPResult:
    require_non_negative(target, "target")

    @lru_cache(maxsize=None)
    def solve(index: int, remaining: int) -> bool:
        if remaining == 0:
            return True
        if index == len(nums) or remaining < 0:
            return False
        return solve(index + 1, remaining) or solve(index + 1, remaining - nums[index])

    return DPResult("Subset Sum", "memoization", solve(0, target))


def tabulated(nums: list[int], target: int) -> DPResult:
    require_non_negative(target, "target")
    dp = [[False] * (target + 1) for _ in range(len(nums) + 1)]
    dp[0][0] = True
    for i, num in enumerate(nums, start=1):
        for total in range(target + 1):
            dp[i][total] = dp[i - 1][total] or (total >= num and dp[i - 1][total - num])
    subset: list[int] = []
    i, total = len(nums), target
    while i > 0 and total > 0:
        if dp[i - 1][total]:
            i -= 1
        else:
            subset.append(nums[i - 1])
            total -= nums[i - 1]
            i -= 1
    subset.reverse()
    return DPResult("Subset Sum", "tabulation", dp[-1][target], {"subset": subset if dp[-1][target] else []})


def space_optimized(nums: list[int], target: int) -> DPResult:
    require_non_negative(target, "target")
    dp = [False] * (target + 1)
    dp[0] = True
    for num in nums:
        for total in range(target, num - 1, -1):
            dp[total] = dp[total] or dp[total - num]
    return DPResult("Subset Sum", "space optimized", dp[target])
