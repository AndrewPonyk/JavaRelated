"""Monotonic-chain convex hull."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True, order=True)
class Point:
    x: float
    y: float


def monotonic_chain(points: list[Point]) -> list[Point]:
    """Return hull vertices in counter-clockwise order, excluding interior collinear points."""
    if points is None:
        raise ValueError("points must not be None")
    if any(point is None for point in points):
        raise ValueError("points must not contain None values")
    sorted_points = sorted(set(points))
    if len(sorted_points) <= 1:
        return sorted_points

    lower: list[Point] = []
    for point in sorted_points:
        while len(lower) >= 2 and _cross(lower[-2], lower[-1], point) <= 0:
            lower.pop()
        lower.append(point)

    upper: list[Point] = []
    for point in reversed(sorted_points):
        while len(upper) >= 2 and _cross(upper[-2], upper[-1], point) <= 0:
            upper.pop()
        upper.append(point)

    return lower[:-1] + upper[:-1]


def _cross(origin: Point, a: Point, b: Point) -> float:
    return (a.x - origin.x) * (b.y - origin.y) - (a.y - origin.y) * (b.x - origin.x)
