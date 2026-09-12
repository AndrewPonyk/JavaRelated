from __future__ import annotations

from functools import lru_cache

from dp_algorithms.common import DPResult


def _validate(dimensions: list[int]) -> None:
    if len(dimensions) < 2:
        raise ValueError("at least one matrix requires two dimensions")
    if any(value <= 0 for value in dimensions):
        raise ValueError("dimensions must be positive")


def memoized(dimensions: list[int]) -> DPResult:
    _validate(dimensions)

    @lru_cache(maxsize=None)
    def solve(i: int, j: int) -> int:
        if i == j:
            return 0
        return min(
            solve(i, k) + solve(k + 1, j) + dimensions[i - 1] * dimensions[k] * dimensions[j]
            for k in range(i, j)
        )

    return DPResult("Matrix Chain", "memoization", solve(1, len(dimensions) - 1))


def tabulated(dimensions: list[int]) -> DPResult:
    _validate(dimensions)
    n = len(dimensions) - 1
    dp = [[0] * (n + 1) for _ in range(n + 1)]
    split = [[0] * (n + 1) for _ in range(n + 1)]
    for length in range(2, n + 1):
        for i in range(1, n - length + 2):
            j = i + length - 1
            dp[i][j] = 10**18
            for k in range(i, j):
                cost = dp[i][k] + dp[k + 1][j] + dimensions[i - 1] * dimensions[k] * dimensions[j]
                if cost < dp[i][j]:
                    dp[i][j] = cost
                    split[i][j] = k

    def build(i: int, j: int) -> str:
        if i == j:
            return f"A{i}"
        k = split[i][j]
        return f"({build(i, k)} x {build(k + 1, j)})"

    return DPResult("Matrix Chain", "tabulation", dp[1][n], {"parenthesization": build(1, n)})


def space_optimized(dimensions: list[int]) -> DPResult:
    # Full matrix-chain reconstruction needs the full split table; this variant keeps the value API.
    result = memoized(dimensions)
    return DPResult("Matrix Chain", "compact memoization", result.value)
