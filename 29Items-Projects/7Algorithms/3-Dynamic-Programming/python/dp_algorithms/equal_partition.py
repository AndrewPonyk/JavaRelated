from __future__ import annotations

from dp_algorithms import subset_sum
from dp_algorithms.common import DPResult


def memoized(nums: list[int]) -> DPResult:
    total = sum(nums)
    value = total % 2 == 0 and bool(subset_sum.memoized(nums, total // 2).value)
    return DPResult("Equal Partition", "memoization", value)


def tabulated(nums: list[int]) -> DPResult:
    total = sum(nums)
    if total % 2 != 0:
        return DPResult("Equal Partition", "tabulation", False, {"target": None, "subset": []})
    result = subset_sum.tabulated(nums, total // 2)
    return DPResult("Equal Partition", "tabulation", result.value, {"target": total // 2, "subset": result.details["subset"]})


def space_optimized(nums: list[int]) -> DPResult:
    total = sum(nums)
    value = total % 2 == 0 and bool(subset_sum.space_optimized(nums, total // 2).value)
    return DPResult("Equal Partition", "space optimized", value)
