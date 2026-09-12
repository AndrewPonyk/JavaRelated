from __future__ import annotations

from functools import lru_cache

from dp_algorithms.common import DPResult


def memoized(rows: int, cols: int) -> DPResult:
    @lru_cache(maxsize=None)
    def solve(row: int, col: int) -> int:
        if row == rows - 1 and col == cols - 1:
            return 1
        if row >= rows or col >= cols:
            return 0
        return solve(row + 1, col) + solve(row, col + 1)

    return DPResult("Unique Paths", "memoization", solve(0, 0))


def tabulated(rows: int, cols: int) -> DPResult:
    dp = [[1] * cols for _ in range(rows)]
    for row in range(1, rows):
        for col in range(1, cols):
            dp[row][col] = dp[row - 1][col] + dp[row][col - 1]
    return DPResult("Unique Paths", "tabulation", dp[-1][-1], {"last_row": dp[-1]})


def space_optimized(rows: int, cols: int) -> DPResult:
    dp = [1] * cols
    for _ in range(1, rows):
        for col in range(1, cols):
            dp[col] += dp[col - 1]
    return DPResult("Unique Paths", "space optimized", dp[-1])
