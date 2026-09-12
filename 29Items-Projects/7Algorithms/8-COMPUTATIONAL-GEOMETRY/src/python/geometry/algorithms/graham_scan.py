from geometry.model.point import Point2D
from geometry.util.numeric import DEFAULT_EPSILON, orientation


def convex_hull(points: list[Point2D]) -> list[Point2D]:
    unique = sorted(set(points))
    if len(unique) <= 1:
        return unique

    lower: list[Point2D] = []
    for point in unique:
        while len(lower) >= 2 and orientation(lower[-2], lower[-1], point) <= DEFAULT_EPSILON:
            lower.pop()
        lower.append(point)

    upper: list[Point2D] = []
    for point in reversed(unique):
        while len(upper) >= 2 and orientation(upper[-2], upper[-1], point) <= DEFAULT_EPSILON:
            upper.pop()
        upper.append(point)

    return lower[:-1] + upper[:-1]
