from enum import Enum

from geometry.model.point import Point2D
from geometry.model.polygon import Polygon2D
from geometry.util.numeric import DEFAULT_EPSILON, on_segment


class Location(str, Enum):
    INSIDE = "inside"
    OUTSIDE = "outside"
    BOUNDARY = "boundary"


def locate_point(polygon: Polygon2D, point: Point2D) -> Location:
    inside = False
    vertices = polygon.vertices

    for index, b in enumerate(vertices):
        a = vertices[index - 1]
        if on_segment(a, b, point, DEFAULT_EPSILON):
            return Location.BOUNDARY

        crosses = (a.y > point.y) != (b.y > point.y)
        if crosses:
            x_at_y = (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x
            if point.x < x_at_y:
                inside = not inside

    return Location.INSIDE if inside else Location.OUTSIDE
