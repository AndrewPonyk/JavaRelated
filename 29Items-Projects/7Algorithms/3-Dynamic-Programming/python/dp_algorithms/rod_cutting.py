from __future__ import annotations

from functools import lru_cache

from dp_algorithms.common import DPResult


def memoized(prices: list[int], length: int) -> DPResult:
    @lru_cache(maxsize=None)
    def solve(remaining: int) -> int:
        if remaining == 0:
            return 0
        return max(prices[cut - 1] + solve(remaining - cut) for cut in range(1, remaining + 1))

    return DPResult("Rod Cutting", "memoization", solve(length))


def tabulated(prices: list[int], length: int) -> DPResult:
    dp = [0] * (length + 1)
    parent = [0] * (length + 1)
    for size in range(1, length + 1):
        for cut in range(1, size + 1):
            value = prices[cut - 1] + dp[size - cut]
            if value > dp[size]:
                dp[size] = value
                parent[size] = cut
    cuts: list[int] = []
    size = length
    while size > 0:
        cuts.append(parent[size])
        size -= parent[size]
    return DPResult("Rod Cutting", "tabulation", dp[length], {"cuts": cuts})


def space_optimized(prices: list[int], length: int) -> DPResult:
    result = tabulated(prices, length)
    return DPResult("Rod Cutting", "space optimized", result.value, result.details)
