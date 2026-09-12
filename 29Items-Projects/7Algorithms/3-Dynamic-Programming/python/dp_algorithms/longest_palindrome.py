from __future__ import annotations

from functools import lru_cache

from dp_algorithms.common import DPResult


def memoized(text: str) -> DPResult:
    @lru_cache(maxsize=None)
    def is_palindrome(left: int, right: int) -> bool:
        return left >= right or (text[left] == text[right] and is_palindrome(left + 1, right - 1))

    best = ""
    for left in range(len(text)):
        for right in range(left, len(text)):
            if right - left + 1 > len(best) and is_palindrome(left, right):
                best = text[left : right + 1]
    return DPResult("Longest Palindrome", "memoization", len(best), {"substring": best})


def tabulated(text: str) -> DPResult:
    n = len(text)
    if n == 0:
        return DPResult("Longest Palindrome", "tabulation", 0, {"substring": ""})
    dp = [[False] * n for _ in range(n)]
    start, best = 0, 1
    for right in range(n):
        for left in range(right + 1):
            if text[left] == text[right] and (right - left <= 2 or dp[left + 1][right - 1]):
                dp[left][right] = True
                if right - left + 1 > best:
                    start, best = left, right - left + 1
    return DPResult("Longest Palindrome", "tabulation", best, {"substring": text[start : start + best]})


def space_optimized(text: str) -> DPResult:
    def expand(left: int, right: int) -> tuple[int, int]:
        while left >= 0 and right < len(text) and text[left] == text[right]:
            left -= 1
            right += 1
        return left + 1, right

    best = (0, 0)
    for center in range(len(text)):
        for left, right in (expand(center, center), expand(center, center + 1)):
            if right - left > best[1] - best[0]:
                best = (left, right)
    substring = text[best[0] : best[1]]
    return DPResult("Longest Palindrome", "center expansion/space optimized", len(substring), {"substring": substring})
