from __future__ import annotations

from functools import lru_cache

from dp_algorithms.common import DPResult, require_non_negative


def _validate(weights: list[int], values: list[int], capacity: int) -> None:
    require_non_negative(capacity, "capacity")
    if len(weights) != len(values):
        raise ValueError("weights and values must have the same length")
    if any(weight < 0 for weight in weights):
        raise ValueError("weights must be non-negative")


def memoized(weights: list[int], values: list[int], capacity: int) -> DPResult:
    _validate(weights, values, capacity)

    @lru_cache(maxsize=None)
    def solve(index: int, remaining: int) -> int:
        if index == len(weights) or remaining == 0:
            return 0
        skip = solve(index + 1, remaining)
        take = 0
        if weights[index] <= remaining:
            take = values[index] + solve(index + 1, remaining - weights[index])
        return max(skip, take)

    return DPResult("0/1 Knapsack", "memoization", solve(0, capacity))


def tabulated(weights: list[int], values: list[int], capacity: int) -> DPResult:
    _validate(weights, values, capacity)
    n = len(weights)
    dp = [[0] * (capacity + 1) for _ in range(n + 1)]
    for i in range(1, n + 1):
        weight = weights[i - 1]
        value = values[i - 1]
        for cap in range(capacity + 1):
            dp[i][cap] = dp[i - 1][cap]
            if weight <= cap:
                dp[i][cap] = max(dp[i][cap], value + dp[i - 1][cap - weight])

    chosen: list[int] = []
    cap = capacity
    for i in range(n, 0, -1):
        if dp[i][cap] != dp[i - 1][cap]:
            chosen.append(i - 1)
            cap -= weights[i - 1]
    chosen.reverse()
    return DPResult("0/1 Knapsack", "tabulation", dp[n][capacity], {"items": chosen})


def space_optimized(weights: list[int], values: list[int], capacity: int) -> DPResult:
    _validate(weights, values, capacity)
    dp = [0] * (capacity + 1)
    for weight, value in zip(weights, values, strict=True):
        for cap in range(capacity, weight - 1, -1):
            dp[cap] = max(dp[cap], value + dp[cap - weight])
    return DPResult("0/1 Knapsack", "space optimized", dp[capacity])
