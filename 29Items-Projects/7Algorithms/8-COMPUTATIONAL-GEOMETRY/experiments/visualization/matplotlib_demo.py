"""Optional visualization experiment for the Python convex hull demo."""

from geometry.algorithms.graham_scan import convex_hull
from geometry.model.point import Point2D


def main() -> None:
    try:
        import matplotlib.pyplot as plt
    except ImportError as exc:
        raise SystemExit("Install matplotlib with: pip install -e .[dev]") from exc

    points = [
        Point2D(0, 0),
        Point2D(1, 2),
        Point2D(2, 1),
        Point2D(0.5, 1),
        Point2D(2, 0),
        Point2D(1, -0.5),
    ]
    hull = convex_hull(points)
    closed_hull = [*hull, hull[0]]

    plt.scatter([p.x for p in points], [p.y for p in points], label="points")
    plt.plot([p.x for p in closed_hull], [p.y for p in closed_hull], label="convex hull")
    plt.axis("equal")
    plt.legend()
    plt.title("Graham Scan Convex Hull")
    plt.show()


if __name__ == "__main__":
    main()
