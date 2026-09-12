from dataclasses import dataclass

from geometry.model.point import Point2D
from geometry.model.segment import Segment2D
from geometry.util.numeric import DEFAULT_EPSILON, on_segment, orientation, sign


@dataclass(frozen=True)
class Intersection:
    first: Segment2D
    second: Segment2D
    point: Point2D | None
    kind: str


def find_intersections(segments: list[Segment2D]) -> list[Intersection]:
    events: list[tuple[float, int, int, Segment2D]] = []
    for index, segment in enumerate(segments):
        min_x = min(segment.start.x, segment.end.x)
        max_x = max(segment.start.x, segment.end.x)
        events.append((min_x, 0, index, segment))
        events.append((max_x, 1, index, segment))
    events.sort(key=lambda event: (event[0], event[1], event[2]))

    intersections: list[Intersection] = []
    seen_pairs: set[tuple[int, int]] = set()
    active: dict[int, Segment2D] = {}
    for _, event_type, index, segment in events:
        if event_type == 0:
            for other_index, other_segment in active.items():
                pair = tuple(sorted((index, other_index)))
                if pair in seen_pairs:
                    continue
                intersection = segment_intersection(segment, other_segment)
                if intersection is not None:
                    intersections.append(intersection)
                    seen_pairs.add(pair)
            active[index] = segment
        else:
            active.pop(index, None)
    return intersections


def segment_intersection(first: Segment2D, second: Segment2D) -> Intersection | None:
    a, b = first.start, first.end
    c, d = second.start, second.end
    eps = DEFAULT_EPSILON

    o1 = sign(orientation(a, b, c), eps)
    o2 = sign(orientation(a, b, d), eps)
    o3 = sign(orientation(c, d, a), eps)
    o4 = sign(orientation(c, d, b), eps)

    if o1 != o2 and o3 != o4:
        return Intersection(first, second, _intersection_point(a, b, c, d), "point")

    boundary_points = []
    for point in (a, b):
        if on_segment(c, d, point, eps):
            boundary_points.append(point)
    for point in (c, d):
        if on_segment(a, b, point, eps) and point not in boundary_points:
            boundary_points.append(point)

    if len(boundary_points) == 1:
        return Intersection(first, second, boundary_points[0], "point")
    if len(boundary_points) > 1:
        return Intersection(first, second, None, "overlap")
    return None


def _intersection_point(a: Point2D, b: Point2D, c: Point2D, d: Point2D) -> Point2D:
    x1, y1 = a.x, a.y
    x2, y2 = b.x, b.y
    x3, y3 = c.x, c.y
    x4, y4 = d.x, d.y
    denominator = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
    if abs(denominator) <= DEFAULT_EPSILON:
        return a
    px = ((x1 * y2 - y1 * x2) * (x3 - x4) - (x1 - x2) * (x3 * y4 - y3 * x4)) / denominator
    py = ((x1 * y2 - y1 * x2) * (y3 - y4) - (y1 - y2) * (x3 * y4 - y3 * x4)) / denominator
    return Point2D(px, py)
