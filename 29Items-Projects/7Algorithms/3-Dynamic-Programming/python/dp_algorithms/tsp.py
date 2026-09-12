from __future__ import annotations

from functools import lru_cache

from dp_algorithms.common import DPResult


def memoized(dist: list[list[int]]) -> DPResult:
    n = len(dist)

    @lru_cache(maxsize=None)
    def solve(city: int, mask: int) -> int:
        if mask == (1 << n) - 1:
            return dist[city][0]
        return min(dist[city][next_city] + solve(next_city, mask | (1 << next_city)) for next_city in range(n) if not mask & (1 << next_city))

    return DPResult("Traveling Salesman", "memoization/bitmask", solve(0, 1))


def tabulated(dist: list[list[int]]) -> DPResult:
    n = len(dist)
    full = 1 << n
    dp = [[10**18] * n for _ in range(full)]
    parent = [[-1] * n for _ in range(full)]
    dp[1][0] = 0
    for mask in range(full):
        for city in range(n):
            if dp[mask][city] >= 10**18:
                continue
            for next_city in range(n):
                if mask & (1 << next_city):
                    continue
                next_mask = mask | (1 << next_city)
                cost = dp[mask][city] + dist[city][next_city]
                if cost < dp[next_mask][next_city]:
                    dp[next_mask][next_city] = cost
                    parent[next_mask][next_city] = city
    best_city = min(range(1, n), key=lambda city: dp[full - 1][city] + dist[city][0])
    best_cost = dp[full - 1][best_city] + dist[best_city][0]
    path = [0]
    stack: list[int] = []
    mask, city = full - 1, best_city
    while city != -1:
        stack.append(city)
        prev = parent[mask][city]
        mask ^= 1 << city
        city = prev
    path.extend(reversed(stack[:-1]))
    path.append(0)
    return DPResult("Traveling Salesman", "tabulation/bitmask", best_cost, {"tour": path})


def space_optimized(dist: list[list[int]]) -> DPResult:
    # TSP requires subset state; bitmask DP is already the compact practical representation.
    result = memoized(dist)
    return DPResult("Traveling Salesman", "compact bitmask memoization", result.value)
