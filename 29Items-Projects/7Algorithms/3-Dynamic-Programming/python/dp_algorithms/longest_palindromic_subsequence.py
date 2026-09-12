from __future__ import annotations

from functools import lru_cache

from dp_algorithms.common import DPResult


def memoized(text: str) -> DPResult:
    @lru_cache(maxsize=None)
    def solve(left: int, right: int) -> int:
        if left > right:
            return 0
        if left == right:
            return 1
        if text[left] == text[right]:
            return 2 + solve(left + 1, right - 1)
        return max(solve(left + 1, right), solve(left, right - 1))

    return DPResult("Longest Palindromic Subsequence", "memoization", solve(0, len(text) - 1))


def tabulated(text: str) -> DPResult:
    n = len(text)
    if n == 0:
        return DPResult("Longest Palindromic Subsequence", "tabulation", 0, {"sequence": ""})
    dp = [[0] * n for _ in range(n)]
    for i in range(n):
        dp[i][i] = 1
    for length in range(2, n + 1):
        for left in range(n - length + 1):
            right = left + length - 1
            if text[left] == text[right]:
                dp[left][right] = 2 + (dp[left + 1][right - 1] if length > 2 else 0)
            else:
                dp[left][right] = max(dp[left + 1][right], dp[left][right - 1])
    left, right = 0, n - 1
    front: list[str] = []
    back: list[str] = []
    while left <= right:
        if left == right:
            front.append(text[left])
            break
        if text[left] == text[right]:
            front.append(text[left])
            back.append(text[right])
            left += 1
            right -= 1
        elif dp[left + 1][right] >= dp[left][right - 1]:
            left += 1
        else:
            right -= 1
    return DPResult("Longest Palindromic Subsequence", "tabulation", dp[0][n - 1], {"sequence": "".join(front + back[::-1])})


def space_optimized(text: str) -> DPResult:
    n = len(text)
    dp = [0] * n
    for left in range(n - 1, -1, -1):
        previous_diagonal = 0
        dp[left] = 1
        for right in range(left + 1, n):
            saved = dp[right]
            if text[left] == text[right]:
                dp[right] = 2 + previous_diagonal
            else:
                dp[right] = max(dp[right], dp[right - 1])
            previous_diagonal = saved
    return DPResult("Longest Palindromic Subsequence", "space optimized", dp[-1] if dp else 0)
