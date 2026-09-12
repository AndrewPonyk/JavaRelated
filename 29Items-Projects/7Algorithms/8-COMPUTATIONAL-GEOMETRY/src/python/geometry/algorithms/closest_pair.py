from dataclasses import dataclass
from math import sqrt

from geometry.model.point import Point2D


@dataclass(frozen=True)
class ClosestPairResult:
    first: Point2D
    second: Point2D
    distance: float


def closest_pair(points: list[Point2D]) -> ClosestPairResult:
    if len(points) < 2:
        raise ValueError("closest pair requires at least two points")

    seen: set[Point2D] = set()
    for point in points:
        if point in seen:
            return ClosestPairResult(point, point, 0.0)
        seen.add(point)

    points_by_x = sorted(points)
    points_by_y = sorted(points, key=lambda p: (p.y, p.x))
    first, second, distance_squared = _closest_recursive(points_by_x, points_by_y)
    return ClosestPairResult(first, second, sqrt(distance_squared))


def _closest_recursive(
    points_by_x: list[Point2D],
    points_by_y: list[Point2D],
) -> tuple[Point2D, Point2D, float]:
    if len(points_by_x) <= 3:
        return _brute_force(points_by_x)

    midpoint = len(points_by_x) // 2
    left_x = points_by_x[:midpoint]
    right_x = points_by_x[midpoint:]
    split_x = points_by_x[midpoint].x
    left_set = set(left_x)
    left_y = [point for point in points_by_y if point in left_set]
    right_y = [point for point in points_by_y if point not in left_set]

    left_best = _closest_recursive(left_x, left_y)
    right_best = _closest_recursive(right_x, right_y)
    best = left_best if left_best[2] <= right_best[2] else right_best
    best_distance_squared = best[2]

    strip = [
        point
        for point in points_by_y
        if (point.x - split_x) * (point.x - split_x) < best_distance_squared
    ]
    for i, first in enumerate(strip):
        for second in strip[i + 1 : i + 8]:
            candidate = first.distance_squared(second)
            if candidate < best_distance_squared:
                best = (first, second, candidate)
                best_distance_squared = candidate

    return best


def _brute_force(points: list[Point2D]) -> tuple[Point2D, Point2D, float]:
    best_first = points[0]
    best_second = points[1]
    best_distance_squared = best_first.distance_squared(best_second)
    for i, first in enumerate(points):
        for second in points[i + 1 :]:
            candidate = first.distance_squared(second)
            if candidate < best_distance_squared:
                best_first = first
                best_second = second
                best_distance_squared = candidate
    return best_first, best_second, best_distance_squared
