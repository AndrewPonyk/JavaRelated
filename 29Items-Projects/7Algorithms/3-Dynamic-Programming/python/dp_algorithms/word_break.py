from __future__ import annotations

from functools import lru_cache

from dp_algorithms.common import DPResult


def memoized(text: str, dictionary: set[str]) -> DPResult:
    @lru_cache(maxsize=None)
    def solve(start: int) -> bool:
        return start == len(text) or any(
            text.startswith(word, start) and solve(start + len(word)) for word in dictionary
        )

    return DPResult("Word Break", "memoization", solve(0))


def tabulated(text: str, dictionary: set[str]) -> DPResult:
    dp = [False] * (len(text) + 1)
    parent = [-1] * (len(text) + 1)
    dp[0] = True
    for end in range(1, len(text) + 1):
        for start in range(end):
            if dp[start] and text[start:end] in dictionary:
                dp[end] = True
                parent[end] = start
                break
    words: list[str] = []
    end = len(text)
    while end > 0 and parent[end] != -1:
        start = parent[end]
        words.append(text[start:end])
        end = start
    words.reverse()
    return DPResult("Word Break", "tabulation", dp[-1], {"words": words if dp[-1] else []})


def space_optimized(text: str, dictionary: set[str]) -> DPResult:
    # The one-dimensional table is already the natural space-optimized representation.
    result = tabulated(text, dictionary)
    return DPResult("Word Break", "space optimized", result.value, result.details)
