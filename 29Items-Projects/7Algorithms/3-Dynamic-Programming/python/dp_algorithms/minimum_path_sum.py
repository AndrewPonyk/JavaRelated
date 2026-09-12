from __future__ import annotations

from functools import lru_cache

from dp_algorithms.common import DPResult


def memoized(grid: list[list[int]]) -> DPResult:
    rows, cols = len(grid), len(grid[0])

    @lru_cache(maxsize=None)
    def solve(row: int, col: int) -> int:
        if row >= rows or col >= cols:
            return 10**12
        if row == rows - 1 and col == cols - 1:
            return grid[row][col]
        return grid[row][col] + min(solve(row + 1, col), solve(row, col + 1))

    return DPResult("Minimum Path Sum", "memoization", solve(0, 0))


def tabulated(grid: list[list[int]]) -> DPResult:
    rows, cols = len(grid), len(grid[0])
    dp = [[0] * cols for _ in range(rows)]
    parent: dict[tuple[int, int], tuple[int, int]] = {}
    dp[0][0] = grid[0][0]
    for row in range(rows):
        for col in range(cols):
            if row == 0 and col == 0:
                continue
            top = dp[row - 1][col] if row > 0 else 10**12
            left = dp[row][col - 1] if col > 0 else 10**12
            if top <= left:
                dp[row][col] = grid[row][col] + top
                parent[(row, col)] = (row - 1, col)
            else:
                dp[row][col] = grid[row][col] + left
                parent[(row, col)] = (row, col - 1)
    path: list[tuple[int, int]] = []
    at = (rows - 1, cols - 1)
    while at in parent:
        path.append(at)
        at = parent[at]
    path.append((0, 0))
    path.reverse()
    return DPResult("Minimum Path Sum", "tabulation", dp[-1][-1], {"path": path})


def space_optimized(grid: list[list[int]]) -> DPResult:
    cols = len(grid[0])
    dp = [10**12] * cols
    dp[0] = 0
    for row in grid:
        dp[0] += row[0]
        for col in range(1, cols):
            dp[col] = row[col] + min(dp[col], dp[col - 1])
    return DPResult("Minimum Path Sum", "space optimized", dp[-1])
