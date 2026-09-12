from dataclasses import dataclass

from geometry.model.point import Point2D


@dataclass(frozen=True)
class Segment2D:
    start: Point2D
    end: Point2D

    def __post_init__(self) -> None:
        if self.start is None or self.end is None:
            raise ValueError("segment endpoints must not be None")
        if self.start == self.end:
            raise ValueError("segment endpoints must be distinct")
