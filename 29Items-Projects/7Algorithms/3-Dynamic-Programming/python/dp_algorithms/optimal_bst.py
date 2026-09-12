from __future__ import annotations

from functools import lru_cache

from dp_algorithms.common import DPResult


def memoized(keys: list[str], freq: list[int]) -> DPResult:
    prefix = [0]
    for value in freq:
        prefix.append(prefix[-1] + value)

    def total(i: int, j: int) -> int:
        return prefix[j + 1] - prefix[i]

    @lru_cache(maxsize=None)
    def solve(i: int, j: int) -> int:
        if i > j:
            return 0
        return min(solve(i, root - 1) + solve(root + 1, j) + total(i, j) for root in range(i, j + 1))

    return DPResult("Optimal BST", "memoization", solve(0, len(keys) - 1))


def tabulated(keys: list[str], freq: list[int]) -> DPResult:
    n = len(keys)
    prefix = [0]
    for value in freq:
        prefix.append(prefix[-1] + value)
    dp = [[0] * n for _ in range(n)]
    root_at = [[-1] * n for _ in range(n)]
    for i in range(n):
        dp[i][i] = freq[i]
        root_at[i][i] = i
    for length in range(2, n + 1):
        for i in range(0, n - length + 1):
            j = i + length - 1
            total = prefix[j + 1] - prefix[i]
            dp[i][j] = 10**18
            for root in range(i, j + 1):
                left = dp[i][root - 1] if root > i else 0
                right = dp[root + 1][j] if root < j else 0
                if left + right + total < dp[i][j]:
                    dp[i][j] = left + right + total
                    root_at[i][j] = root

    def build(i: int, j: int) -> str:
        if i > j:
            return "."
        root = root_at[i][j]
        return f"{keys[root]}({build(i, root - 1)},{build(root + 1, j)})"

    return DPResult("Optimal BST", "tabulation", dp[0][n - 1], {"tree": build(0, n - 1)})


def space_optimized(keys: list[str], freq: list[int]) -> DPResult:
    # Optimal BST needs interval state for correctness; expose memoized value as the compact variant.
    result = memoized(keys, freq)
    return DPResult("Optimal BST", "compact memoization", result.value)
