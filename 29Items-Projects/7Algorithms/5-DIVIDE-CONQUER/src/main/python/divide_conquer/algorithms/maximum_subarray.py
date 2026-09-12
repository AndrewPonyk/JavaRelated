"""Divide-and-conquer maximum subarray."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Result:
    start: int
    end: int
    total: int


def find(values: list[int]) -> Result:
    """Return the maximum-sum contiguous subarray."""
    if not values:
        raise ValueError("values must not be empty")
    return _find(values, 0, len(values) - 1)


def _find(values: list[int], left: int, right: int) -> Result:
    if left == right:
        return Result(left, right, values[left])

    mid = left + (right - left) // 2
    left_best = _find(values, left, mid)
    right_best = _find(values, mid + 1, right)
    crossing_best = _crossing(values, left, mid, right)
    return max(left_best, right_best, crossing_best, key=lambda result: result.total)


def _crossing(values: list[int], left: int, mid: int, right: int) -> Result:
    best_left_sum = -10**18
    current = 0
    best_left = mid
    for index in range(mid, left - 1, -1):
        current += values[index]
        if current > best_left_sum:
            best_left_sum = current
            best_left = index

    best_right_sum = -10**18
    current = 0
    best_right = mid + 1
    for index in range(mid + 1, right + 1):
        current += values[index]
        if current > best_right_sum:
            best_right_sum = current
            best_right = index

    return Result(best_left, best_right, best_left_sum + best_right_sum)

