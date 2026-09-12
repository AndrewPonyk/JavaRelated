import argparse
import os

from geometry.algorithms.closest_pair import closest_pair
from geometry.algorithms.delaunay import triangulate
from geometry.algorithms.graham_scan import convex_hull
from geometry.algorithms.line_sweep import find_intersections
from geometry.algorithms.point_in_polygon import locate_point
from geometry.algorithms.voronoi import build_voronoi
from geometry.model.point import Point2D
from geometry.model.polygon import Polygon2D
from geometry.model.segment import Segment2D
from geometry.util.formatting import print_list
from geometry.util.input_loader import load_points


def default_points() -> list[Point2D]:
    return [
        Point2D(0, 0),
        Point2D(1, 2),
        Point2D(2, 1),
        Point2D(0.5, 1),
        Point2D(2, 0),
        Point2D(1, -0.5),
    ]


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description="Run computational geometry console demos.")
    parser.add_argument(
        "--algorithm",
        choices=["all", "hull", "closest", "polygon", "sweep", "delaunay", "voronoi"],
        default="all",
    )
    parser.add_argument("--input", help="Optional CSV or JSON point file with x/y coordinates.")
    args = parser.parse_args(argv)

    max_points = _max_points_from_env()
    points = load_points(args.input, max_points=max_points) if args.input else default_points()

    print("Computational Geometry Demo - Python")
    print("====================================")
    print_list("Input points", points)

    if args.algorithm in {"all", "hull", "polygon"}:
        hull = convex_hull(points)
        print_list("Convex hull", hull)
    else:
        hull = []

    if args.algorithm in {"all", "closest"}:
        closest = closest_pair(points)
        print(
            f"Closest pair: {closest.first} <-> {closest.second}, "
            f"distance {closest.distance:.3f}"
        )

    if args.algorithm in {"all", "polygon"} and len(hull) >= 3:
        polygon = Polygon2D(hull)
        query = Point2D(1, 1)
        print(f"Point in polygon for {query}: {locate_point(polygon, query).value}")

    if args.algorithm in {"all", "sweep"}:
        segments = [
            Segment2D(Point2D(0, 0), Point2D(2, 2)),
            Segment2D(Point2D(0, 2), Point2D(2, 0)),
            Segment2D(Point2D(3, 0), Point2D(3, 2)),
        ]
        print_list("Segment intersections", find_intersections(segments))

    if args.algorithm in {"all", "delaunay"}:
        print_list("Delaunay triangles", triangulate(points))

    if args.algorithm in {"all", "voronoi"}:
        print_list("Voronoi cells", build_voronoi(points))


def _max_points_from_env() -> int:
    raw_value = os.getenv("GEOMETRY_MAX_INPUT_POINTS", "1000000")
    try:
        value = int(raw_value)
    except ValueError as exc:
        raise SystemExit("GEOMETRY_MAX_INPUT_POINTS must be an integer") from exc
    if value < 1:
        raise SystemExit("GEOMETRY_MAX_INPUT_POINTS must be at least 1")
    return value


if __name__ == "__main__":
    main()
