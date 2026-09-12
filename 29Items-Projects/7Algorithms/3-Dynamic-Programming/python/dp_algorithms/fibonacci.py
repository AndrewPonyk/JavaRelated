from __future__ import annotations

from functools import lru_cache

from dp_algorithms.common import DPResult, require_non_negative


def memoized(n: int) -> DPResult:
    require_non_negative(n, "n")

    @lru_cache(maxsize=None)
    def solve(k: int) -> int:
        return k if k <= 1 else solve(k - 1) + solve(k - 2)

    return DPResult("Fibonacci", "memoization", solve(n))


def tabulated(n: int) -> DPResult:
    require_non_negative(n, "n")
    if n <= 1:
        return DPResult("Fibonacci", "tabulation", n, {"sequence": list(range(n + 1))})
    dp = [0] * (n + 1)
    dp[1] = 1
    for i in range(2, n + 1):
        dp[i] = dp[i - 1] + dp[i - 2]
    return DPResult("Fibonacci", "tabulation", dp[n], {"sequence": dp})


def space_optimized(n: int) -> DPResult:
    require_non_negative(n, "n")
    previous, current = 0, 1
    for _ in range(n):
        previous, current = current, previous + current
    return DPResult("Fibonacci", "space optimized", previous)
