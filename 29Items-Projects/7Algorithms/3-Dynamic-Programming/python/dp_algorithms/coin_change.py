from __future__ import annotations

from functools import lru_cache

from dp_algorithms.common import DPResult, require_non_negative

INF = 10**9


def memoized(coins: list[int], amount: int) -> DPResult:
    require_non_negative(amount, "amount")

    @lru_cache(maxsize=None)
    def solve(remaining: int) -> int:
        if remaining == 0:
            return 0
        if remaining < 0:
            return INF
        return 1 + min(solve(remaining - coin) for coin in coins)

    value = solve(amount)
    return DPResult("Coin Change", "memoization", -1 if value >= INF else value)


def tabulated(coins: list[int], amount: int) -> DPResult:
    require_non_negative(amount, "amount")
    dp = [INF] * (amount + 1)
    parent = [-1] * (amount + 1)
    dp[0] = 0
    for current in range(1, amount + 1):
        for coin in coins:
            if coin <= current and dp[current - coin] + 1 < dp[current]:
                dp[current] = dp[current - coin] + 1
                parent[current] = coin
    chosen: list[int] = []
    current = amount
    while current > 0 and parent[current] != -1:
        chosen.append(parent[current])
        current -= parent[current]
    value = -1 if dp[amount] >= INF else dp[amount]
    return DPResult("Coin Change", "tabulation", value, {"coins": chosen if value != -1 else []})


def space_optimized(coins: list[int], amount: int) -> DPResult:
    result = tabulated(coins, amount)
    return DPResult("Coin Change", "space optimized", result.value, result.details)
