from __future__ import annotations

from functools import lru_cache

from dp_algorithms.common import DPResult


def memoized(a: str, b: str) -> DPResult:
    @lru_cache(maxsize=None)
    def suffix(i: int, j: int) -> int:
        if i == len(a) or j == len(b) or a[i] != b[j]:
            return 0
        return 1 + suffix(i + 1, j + 1)

    best_len, best_start = 0, 0
    for i in range(len(a)):
        for j in range(len(b)):
            length = suffix(i, j)
            if length > best_len:
                best_len, best_start = length, i
    return DPResult("Longest Common Substring", "memoization", best_len, {"substring": a[best_start : best_start + best_len]})


def tabulated(a: str, b: str) -> DPResult:
    dp = [[0] * (len(b) + 1) for _ in range(len(a) + 1)]
    best_len, end = 0, 0
    for i in range(1, len(a) + 1):
        for j in range(1, len(b) + 1):
            if a[i - 1] == b[j - 1]:
                dp[i][j] = 1 + dp[i - 1][j - 1]
                if dp[i][j] > best_len:
                    best_len, end = dp[i][j], i
    return DPResult("Longest Common Substring", "tabulation", best_len, {"substring": a[end - best_len : end]})


def space_optimized(a: str, b: str) -> DPResult:
    previous = [0] * (len(b) + 1)
    best_len, end = 0, 0
    for i in range(1, len(a) + 1):
        current = [0] * (len(b) + 1)
        for j in range(1, len(b) + 1):
            if a[i - 1] == b[j - 1]:
                current[j] = 1 + previous[j - 1]
                if current[j] > best_len:
                    best_len, end = current[j], i
        previous = current
    return DPResult("Longest Common Substring", "space optimized", best_len, {"substring": a[end - best_len : end]})
