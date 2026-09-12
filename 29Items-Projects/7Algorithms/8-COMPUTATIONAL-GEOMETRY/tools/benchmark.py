"""Small local benchmark for Python algorithm scaling checks."""

from __future__ import annotations

import random
import time

from geometry.algorithms.closest_pair import closest_pair
from geometry.algorithms.graham_scan import convex_hull
from geometry.model.point import Point2D


def main() -> None:
    random.seed(42)
    for size in (100, 1_000, 5_000):
        points = [Point2D(random.random() * 1_000, random.random() * 1_000) for _ in range(size)]
        hull_seconds = _time(lambda current_points=points: convex_hull(current_points))
        closest_seconds = _time(lambda current_points=points: closest_pair(current_points))
        print(
            f"n={size:5d} hull={hull_seconds:.4f}s closest_pair={closest_seconds:.4f}s"
        )


def _time(action) -> float:
    start = time.perf_counter()
    action()
    return time.perf_counter() - start


if __name__ == "__main__":
    main()
