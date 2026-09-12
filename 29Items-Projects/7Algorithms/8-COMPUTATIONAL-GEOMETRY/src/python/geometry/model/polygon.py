from dataclasses import dataclass

from geometry.model.point import Point2D


@dataclass(frozen=True)
class Polygon2D:
    vertices: tuple[Point2D, ...]

    def __init__(self, vertices: list[Point2D] | tuple[Point2D, ...]) -> None:
        if vertices is None:
            raise ValueError("polygon vertices must not be None")
        if len(vertices) < 3:
            raise ValueError("polygon requires at least three vertices")
        if any(vertex is None for vertex in vertices):
            raise ValueError("polygon vertices must not contain None")
        object.__setattr__(self, "vertices", tuple(vertices))
