"""Closest pair of points in O(n log n)."""

from __future__ import annotations

from dataclasses import dataclass
from math import hypot


@dataclass(frozen=True, order=True)
class Point:
    x: float
    y: float


@dataclass(frozen=True)
class Result:
    first: Point
    second: Point
    distance: float


def find(points: list[Point]) -> Result:
    """Return the closest pair among at least two points."""
    if points is None:
        raise ValueError("points must not be None")
    if len(points) < 2:
        raise ValueError("at least two points are required")
    if any(point is None for point in points):
        raise ValueError("points must not contain None values")
    by_x = sorted(points, key=lambda point: (point.x, point.y))
    by_y = sorted(points, key=lambda point: (point.y, point.x))
    return _closest(by_x, by_y)


def _closest(by_x: list[Point], by_y: list[Point]) -> Result:
    if len(by_x) <= 3:
        return _brute_force(by_x)

    mid = len(by_x) // 2
    mid_point = by_x[mid]
    left_x = by_x[:mid]
    right_x = by_x[mid:]
    left_lookup = set(left_x)
    left_y = [point for point in by_y if point in left_lookup]
    right_y = [point for point in by_y if point not in left_lookup]

    left = _closest(left_x, left_y)
    right = _closest(right_x, right_y)
    best = left if left.distance <= right.distance else right

    strip = [point for point in by_y if abs(point.x - mid_point.x) < best.distance]
    for i, point in enumerate(strip):
        for other in strip[i + 1 : i + 8]:
            if other.y - point.y >= best.distance:
                break
            candidate = _result(point, other)
            if candidate.distance < best.distance:
                best = candidate
    return best


def _brute_force(points: list[Point]) -> Result:
    best = _result(points[0], points[1])
    for i, point in enumerate(points):
        for other in points[i + 1 :]:
            candidate = _result(point, other)
            if candidate.distance < best.distance:
                best = candidate
    return best


def _result(a: Point, b: Point) -> Result:
    return Result(a, b, hypot(a.x - b.x, a.y - b.y))
