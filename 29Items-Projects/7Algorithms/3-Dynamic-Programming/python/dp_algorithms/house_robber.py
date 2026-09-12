from __future__ import annotations

from functools import lru_cache

from dp_algorithms.common import DPResult


def memoized(houses: list[int]) -> DPResult:
    @lru_cache(maxsize=None)
    def solve(index: int) -> int:
        if index >= len(houses):
            return 0
        return max(solve(index + 1), houses[index] + solve(index + 2))

    return DPResult("House Robber", "memoization", solve(0))


def tabulated(houses: list[int]) -> DPResult:
    n = len(houses)
    dp = [0] * (n + 2)
    for i in range(n - 1, -1, -1):
        dp[i] = max(dp[i + 1], houses[i] + dp[i + 2])
    chosen: list[int] = []
    i = 0
    while i < n:
        if houses[i] + dp[i + 2] >= dp[i + 1]:
            chosen.append(i)
            i += 2
        else:
            i += 1
    return DPResult("House Robber", "tabulation", dp[0], {"houses": chosen})


def space_optimized(houses: list[int]) -> DPResult:
    next_one = 0
    next_two = 0
    for value in reversed(houses):
        current = max(next_one, value + next_two)
        next_two = next_one
        next_one = current
    return DPResult("House Robber", "space optimized", next_one)
