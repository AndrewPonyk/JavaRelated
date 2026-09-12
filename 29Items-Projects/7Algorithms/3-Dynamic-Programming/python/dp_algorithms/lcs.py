from __future__ import annotations

from functools import lru_cache

from dp_algorithms.common import DPResult


def memoized(a: str, b: str) -> DPResult:
    @lru_cache(maxsize=None)
    def solve(i: int, j: int) -> int:
        if i == len(a) or j == len(b):
            return 0
        if a[i] == b[j]:
            return 1 + solve(i + 1, j + 1)
        return max(solve(i + 1, j), solve(i, j + 1))

    return DPResult("LCS", "memoization", solve(0, 0))


def tabulated(a: str, b: str) -> DPResult:
    dp = [[0] * (len(b) + 1) for _ in range(len(a) + 1)]
    for i in range(1, len(a) + 1):
        for j in range(1, len(b) + 1):
            if a[i - 1] == b[j - 1]:
                dp[i][j] = 1 + dp[i - 1][j - 1]
            else:
                dp[i][j] = max(dp[i - 1][j], dp[i][j - 1])

    i, j = len(a), len(b)
    chars: list[str] = []
    while i > 0 and j > 0:
        if a[i - 1] == b[j - 1]:
            chars.append(a[i - 1])
            i -= 1
            j -= 1
        elif dp[i - 1][j] >= dp[i][j - 1]:
            i -= 1
        else:
            j -= 1
    sequence = "".join(reversed(chars))
    return DPResult("LCS", "tabulation", dp[-1][-1], {"sequence": sequence})


def space_optimized(a: str, b: str) -> DPResult:
    previous = [0] * (len(b) + 1)
    for left in a:
        current = [0] * (len(b) + 1)
        for j, right in enumerate(b, start=1):
            current[j] = 1 + previous[j - 1] if left == right else max(previous[j], current[j - 1])
        previous = current
    return DPResult("LCS", "space optimized", previous[-1])
