from __future__ import annotations

from functools import lru_cache

from dp_algorithms.common import DPResult


def memoized(source: str, target: str) -> DPResult:
    @lru_cache(maxsize=None)
    def solve(i: int, j: int) -> int:
        if i == len(source):
            return len(target) - j
        if j == len(target):
            return len(source) - i
        if source[i] == target[j]:
            return solve(i + 1, j + 1)
        return 1 + min(solve(i + 1, j), solve(i, j + 1), solve(i + 1, j + 1))

    return DPResult("Edit Distance", "memoization", solve(0, 0))


def tabulated(source: str, target: str) -> DPResult:
    rows, cols = len(source) + 1, len(target) + 1
    dp = [[0] * cols for _ in range(rows)]
    for i in range(rows):
        dp[i][0] = i
    for j in range(cols):
        dp[0][j] = j
    for i in range(1, rows):
        for j in range(1, cols):
            if source[i - 1] == target[j - 1]:
                dp[i][j] = dp[i - 1][j - 1]
            else:
                dp[i][j] = 1 + min(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])

    operations: list[str] = []
    i, j = len(source), len(target)
    while i > 0 or j > 0:
        if i > 0 and j > 0 and source[i - 1] == target[j - 1]:
            i -= 1
            j -= 1
        elif i > 0 and dp[i][j] == dp[i - 1][j] + 1:
            operations.append(f"delete {source[i - 1]}")
            i -= 1
        elif j > 0 and dp[i][j] == dp[i][j - 1] + 1:
            operations.append(f"insert {target[j - 1]}")
            j -= 1
        else:
            operations.append(f"replace {source[i - 1]}->{target[j - 1]}")
            i -= 1
            j -= 1
    operations.reverse()
    return DPResult("Edit Distance", "tabulation", dp[-1][-1], {"operations": operations})


def space_optimized(source: str, target: str) -> DPResult:
    previous = list(range(len(target) + 1))
    for i, left in enumerate(source, start=1):
        current = [i] + [0] * len(target)
        for j, right in enumerate(target, start=1):
            if left == right:
                current[j] = previous[j - 1]
            else:
                current[j] = 1 + min(previous[j], current[j - 1], previous[j - 1])
        previous = current
    return DPResult("Edit Distance", "space optimized", previous[-1])
