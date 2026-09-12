"""Skyline problem solved by divide-and-conquer merging."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Building:
    left: int
    right: int
    height: int

    def __post_init__(self) -> None:
        if self.left >= self.right or self.height < 0:
            raise ValueError("building must have left < right and non-negative height")


@dataclass(frozen=True)
class KeyPoint:
    x: int
    height: int


def skyline(buildings: list[Building]) -> list[KeyPoint]:
    """Return critical skyline points for left-to-right buildings."""
    if buildings is None:
        raise ValueError("buildings must not be None")
    if not buildings:
        return []
    return _solve(buildings, 0, len(buildings))


def _solve(buildings: list[Building], start: int, end: int) -> list[KeyPoint]:
    if end - start == 1:
        building = buildings[start]
        return [KeyPoint(building.left, building.height), KeyPoint(building.right, 0)]
    mid = start + (end - start) // 2
    return _merge(_solve(buildings, start, mid), _solve(buildings, mid, end))


def _merge(left: list[KeyPoint], right: list[KeyPoint]) -> list[KeyPoint]:
    result: list[KeyPoint] = []
    left_index = 0
    right_index = 0
    left_height = 0
    right_height = 0

    while left_index < len(left) and right_index < len(right):
        if left[left_index].x < right[right_index].x:
            x = left[left_index].x
            left_height = left[left_index].height
            left_index += 1
        elif right[right_index].x < left[left_index].x:
            x = right[right_index].x
            right_height = right[right_index].height
            right_index += 1
        else:
            x = left[left_index].x
            left_height = left[left_index].height
            right_height = right[right_index].height
            left_index += 1
            right_index += 1
        _append(result, x, max(left_height, right_height))

    for point in left[left_index:]:
        _append(result, point.x, point.height)
    for point in right[right_index:]:
        _append(result, point.x, point.height)
    return result


def _append(result: list[KeyPoint], x: int, height: int) -> None:
    if result and result[-1].height == height:
        return
    if result and result[-1].x == x:
        result[-1] = KeyPoint(x, height)
        return
    result.append(KeyPoint(x, height))

