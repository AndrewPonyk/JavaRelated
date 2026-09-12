from __future__ import annotations

from bisect import bisect_left
from functools import lru_cache

from dp_algorithms.common import DPResult


def memoized(nums: list[int]) -> DPResult:
    @lru_cache(maxsize=None)
    def solve(index: int, previous_index: int) -> int:
        if index == len(nums):
            return 0
        skip = solve(index + 1, previous_index)
        take = 0
        if previous_index == -1 or nums[index] > nums[previous_index]:
            take = 1 + solve(index + 1, index)
        return max(skip, take)

    return DPResult("LIS", "memoization", solve(0, -1))


def tabulated(nums: list[int]) -> DPResult:
    if not nums:
        return DPResult("LIS", "tabulation", 0, {"sequence": []})
    dp = [1] * len(nums)
    parent = [-1] * len(nums)
    for i in range(len(nums)):
        for j in range(i):
            if nums[j] < nums[i] and dp[j] + 1 > dp[i]:
                dp[i] = dp[j] + 1
                parent[i] = j
    index = max(range(len(nums)), key=dp.__getitem__)
    sequence: list[int] = []
    while index != -1:
        sequence.append(nums[index])
        index = parent[index]
    sequence.reverse()
    return DPResult("LIS", "tabulation", len(sequence), {"sequence": sequence})


def space_optimized(nums: list[int]) -> DPResult:
    tails: list[int] = []
    for value in nums:
        pos = bisect_left(tails, value)
        if pos == len(tails):
            tails.append(value)
        else:
            tails[pos] = value
    return DPResult("LIS", "patience/space optimized", len(tails))
