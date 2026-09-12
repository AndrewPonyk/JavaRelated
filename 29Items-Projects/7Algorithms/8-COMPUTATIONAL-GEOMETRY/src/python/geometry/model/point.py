from __future__ import annotations

from dataclasses import dataclass
from math import isfinite


@dataclass(frozen=True, order=True)
class Point2D:
    x: float
    y: float

    def __post_init__(self) -> None:
        if not isfinite(self.x) or not isfinite(self.y):
            raise ValueError("point coordinates must be finite numbers")

    def distance_squared(self, other: Point2D) -> float:
        dx = self.x - other.x
        dy = self.y - other.y
        return dx * dx + dy * dy

    def __str__(self) -> str:
        return f"({self.x:.3f}, {self.y:.3f})"
