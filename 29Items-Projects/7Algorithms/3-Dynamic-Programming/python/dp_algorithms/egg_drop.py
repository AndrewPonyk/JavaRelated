from __future__ import annotations

from functools import lru_cache

from dp_algorithms.common import DPResult


def memoized(eggs: int, floors: int) -> DPResult:
    @lru_cache(maxsize=None)
    def solve(e: int, f: int) -> int:
        if f <= 1 or e == 1:
            return f
        return 1 + min(max(solve(e - 1, x - 1), solve(e, f - x)) for x in range(1, f + 1))

    return DPResult("Egg Drop", "memoization", solve(eggs, floors))


def tabulated(eggs: int, floors: int) -> DPResult:
    dp = [[0] * (floors + 1) for _ in range(eggs + 1)]
    for e in range(1, eggs + 1):
        dp[e][1:] = range(1, floors + 1)
    for e in range(2, eggs + 1):
        for f in range(2, floors + 1):
            dp[e][f] = 1 + min(max(dp[e - 1][x - 1], dp[e][f - x]) for x in range(1, f + 1))
    return DPResult("Egg Drop", "tabulation", dp[eggs][floors])


def space_optimized(eggs: int, floors: int) -> DPResult:
    moves = 0
    reachable = [0] * (eggs + 1)
    while reachable[eggs] < floors:
        moves += 1
        for e in range(eggs, 0, -1):
            reachable[e] = reachable[e] + reachable[e - 1] + 1
    return DPResult("Egg Drop", "moves/space optimized", moves, {"reachable_floors": reachable[eggs]})
