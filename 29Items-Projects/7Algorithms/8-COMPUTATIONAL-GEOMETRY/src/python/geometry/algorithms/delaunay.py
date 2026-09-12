from collections import Counter
from dataclasses import dataclass

from geometry.model.point import Point2D
from geometry.util.numeric import DEFAULT_EPSILON, circumcenter, orientation, sign


@dataclass(frozen=True)
class Edge:
    first: Point2D
    second: Point2D

    def __post_init__(self) -> None:
        if self.second < self.first:
            first = self.first
            second = self.second
            object.__setattr__(self, "first", second)
            object.__setattr__(self, "second", first)


Triangle = tuple[Point2D, Point2D, Point2D]


def triangulate(points: list[Point2D]) -> list[Triangle]:
    unique = sorted(set(points))
    if len(unique) < 3:
        return []
    if _all_collinear(unique):
        return []

    super_triangle = _build_super_triangle(unique)
    triangles: list[Triangle] = [super_triangle]

    for point in unique:
        bad_triangles = [triangle for triangle in triangles if _in_circumcircle(triangle, point)]
        boundary = _boundary_edges(bad_triangles)
        triangles = [triangle for triangle in triangles if triangle not in bad_triangles]
        for edge in boundary:
            candidate = _oriented_triangle(edge.first, edge.second, point)
            if candidate is not None:
                triangles.append(candidate)

    super_vertices = set(super_triangle)
    result = [
        triangle
        for triangle in triangles
        if not any(vertex in super_vertices for vertex in triangle)
    ]
    return sorted(result, key=lambda tri: tuple((point.x, point.y) for point in tri))


def triangle_edges(triangle: Triangle) -> tuple[Edge, Edge, Edge]:
    a, b, c = triangle
    return Edge(a, b), Edge(b, c), Edge(c, a)


def triangle_circumcenter(triangle: Triangle) -> Point2D | None:
    return circumcenter(*triangle)


def _build_super_triangle(points: list[Point2D]) -> Triangle:
    min_x = min(point.x for point in points)
    max_x = max(point.x for point in points)
    min_y = min(point.y for point in points)
    max_y = max(point.y for point in points)
    delta = max(max_x - min_x, max_y - min_y)
    if delta <= DEFAULT_EPSILON:
        delta = 1.0
    mid_x = (min_x + max_x) / 2.0
    mid_y = (min_y + max_y) / 2.0
    span = delta * 64.0
    return (
        Point2D(mid_x - 2.0 * span, mid_y - span),
        Point2D(mid_x, mid_y + 2.0 * span),
        Point2D(mid_x + 2.0 * span, mid_y - span),
    )


def _boundary_edges(triangles: list[Triangle]) -> list[Edge]:
    counts = Counter(edge for triangle in triangles for edge in triangle_edges(triangle))
    return [edge for edge, count in counts.items() if count == 1]


def _in_circumcircle(triangle: Triangle, point: Point2D) -> bool:
    center = triangle_circumcenter(triangle)
    if center is None:
        return False
    radius_squared = center.distance_squared(triangle[0])
    return center.distance_squared(point) <= radius_squared + DEFAULT_EPSILON


def _oriented_triangle(a: Point2D, b: Point2D, c: Point2D) -> Triangle | None:
    turn = sign(orientation(a, b, c))
    if turn == 0:
        return None
    return (a, b, c) if turn > 0 else (a, c, b)


def _all_collinear(points: list[Point2D]) -> bool:
    first = points[0]
    second = points[1]
    return all(sign(orientation(first, second, point)) == 0 for point in points[2:])
