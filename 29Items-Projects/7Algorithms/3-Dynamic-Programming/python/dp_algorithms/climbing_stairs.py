from __future__ import annotations

from functools import lru_cache

from dp_algorithms.common import DPResult, require_non_negative


def memoized(steps: int) -> DPResult:
    require_non_negative(steps, "steps")

    @lru_cache(maxsize=None)
    def solve(n: int) -> int:
        return 1 if n <= 1 else solve(n - 1) + solve(n - 2)

    return DPResult("Climbing Stairs", "memoization", solve(steps))


def tabulated(steps: int) -> DPResult:
    require_non_negative(steps, "steps")
    dp = [0] * (steps + 1)
    dp[0] = 1
    if steps >= 1:
        dp[1] = 1
    for i in range(2, steps + 1):
        dp[i] = dp[i - 1] + dp[i - 2]
    return DPResult("Climbing Stairs", "tabulation", dp[steps], {"ways_by_step": dp})


def space_optimized(steps: int) -> DPResult:
    require_non_negative(steps, "steps")
    one_back, two_back = 1, 1
    for _ in range(2, steps + 1):
        one_back, two_back = one_back + two_back, one_back
    return DPResult("Climbing Stairs", "space optimized", one_back)
