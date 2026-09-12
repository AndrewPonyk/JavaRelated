from geometry.model.point import Point2D

DEFAULT_EPSILON = 1.0e-9


def orientation(a: Point2D, b: Point2D, c: Point2D) -> float:
    return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)


def signed_area(points: list[Point2D] | tuple[Point2D, ...]) -> float:
    if len(points) < 3:
        return 0.0
    total = 0.0
    for index, point in enumerate(points):
        nxt = points[(index + 1) % len(points)]
        total += point.x * nxt.y - point.y * nxt.x
    return total / 2.0


def sign(value: float, epsilon: float = DEFAULT_EPSILON) -> int:
    if value > epsilon:
        return 1
    if value < -epsilon:
        return -1
    return 0


def on_segment(
    a: Point2D,
    b: Point2D,
    point: Point2D,
    epsilon: float = DEFAULT_EPSILON,
) -> bool:
    return (
        sign(orientation(a, b, point), epsilon) == 0
        and min(a.x, b.x) - epsilon <= point.x <= max(a.x, b.x) + epsilon
        and min(a.y, b.y) - epsilon <= point.y <= max(a.y, b.y) + epsilon
    )


def circumcenter(
    a: Point2D,
    b: Point2D,
    c: Point2D,
    epsilon: float = DEFAULT_EPSILON,
) -> Point2D | None:
    denominator = 2.0 * (
        a.x * (b.y - c.y)
        + b.x * (c.y - a.y)
        + c.x * (a.y - b.y)
    )
    if abs(denominator) <= epsilon:
        return None

    a2 = a.x * a.x + a.y * a.y
    b2 = b.x * b.x + b.y * b.y
    c2 = c.x * c.x + c.y * c.y
    x = (a2 * (b.y - c.y) + b2 * (c.y - a.y) + c2 * (a.y - b.y)) / denominator
    y = (a2 * (c.x - b.x) + b2 * (a.x - c.x) + c2 * (b.x - a.x)) / denominator
    return Point2D(x, y)
