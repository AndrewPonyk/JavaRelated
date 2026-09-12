import os
import subprocess
import sys
from math import inf
from pathlib import Path

import pytest
from geometry.algorithms.closest_pair import closest_pair
from geometry.algorithms.delaunay import triangle_circumcenter, triangulate
from geometry.algorithms.graham_scan import convex_hull
from geometry.algorithms.line_sweep import find_intersections, segment_intersection
from geometry.algorithms.point_in_polygon import Location, locate_point
from geometry.algorithms.voronoi import build_voronoi
from geometry.model.point import Point2D
from geometry.model.polygon import Polygon2D
from geometry.model.segment import Segment2D
from geometry.util.input_loader import load_points
from geometry.util.numeric import circumcenter, orientation, sign, signed_area


def test_numeric_predicates_cover_orientation_area_and_circumcenter() -> None:
    a = Point2D(0, 0)
    b = Point2D(1, 0)
    c = Point2D(0, 1)

    assert sign(orientation(a, b, c)) == 1
    assert signed_area([a, b, c]) == pytest.approx(0.5)
    assert circumcenter(a, b, c) == Point2D(0.5, 0.5)


def test_convex_hull_handles_duplicates_and_interior_points() -> None:
    hull = convex_hull(
        [
            Point2D(0, 0),
            Point2D(1, 0),
            Point2D(1, 1),
            Point2D(0, 1),
            Point2D(0.5, 0.5),
            Point2D(1, 1),
        ]
    )

    assert hull == [Point2D(0, 0), Point2D(1, 0), Point2D(1, 1), Point2D(0, 1)]


def test_convex_hull_collinear_points_returns_endpoints() -> None:
    assert convex_hull([Point2D(0, 0), Point2D(1, 0), Point2D(2, 0)]) == [
        Point2D(0, 0),
        Point2D(2, 0),
    ]


def test_point_in_polygon_detects_inside_outside_edge_and_vertex() -> None:
    square = Polygon2D([Point2D(0, 0), Point2D(2, 0), Point2D(2, 2), Point2D(0, 2)])

    assert locate_point(square, Point2D(1, 1)) == Location.INSIDE
    assert locate_point(square, Point2D(3, 1)) == Location.OUTSIDE
    assert locate_point(square, Point2D(2, 1)) == Location.BOUNDARY
    assert locate_point(square, Point2D(0, 0)) == Location.BOUNDARY


def test_closest_pair_uses_zero_distance_for_duplicates() -> None:
    result = closest_pair([Point2D(0, 0), Point2D(2, 2), Point2D(0, 0)])

    assert result.distance == 0.0
    assert result.first == Point2D(0, 0)
    assert result.second == Point2D(0, 0)


def test_closest_pair_finds_minimum_distance() -> None:
    result = closest_pair([Point2D(0, 0), Point2D(5, 5), Point2D(2, 0), Point2D(2, 1)])

    assert result.distance == pytest.approx(1.0)
    assert {result.first, result.second} == {Point2D(2, 0), Point2D(2, 1)}


def test_line_sweep_finds_crossing_touching_and_overlapping_segments() -> None:
    crossing = segment_intersection(
        Segment2D(Point2D(0, 0), Point2D(2, 2)),
        Segment2D(Point2D(0, 2), Point2D(2, 0)),
    )
    touching = segment_intersection(
        Segment2D(Point2D(0, 0), Point2D(1, 1)),
        Segment2D(Point2D(1, 1), Point2D(2, 2)),
    )
    overlap = segment_intersection(
        Segment2D(Point2D(0, 0), Point2D(2, 0)),
        Segment2D(Point2D(1, 0), Point2D(3, 0)),
    )
    disjoint = segment_intersection(
        Segment2D(Point2D(0, 0), Point2D(1, 0)),
        Segment2D(Point2D(0, 1), Point2D(1, 1)),
    )

    assert crossing is not None and crossing.point == Point2D(1, 1)
    assert touching is not None and touching.kind == "point"
    assert overlap is not None and overlap.kind == "overlap"
    assert disjoint is None


def test_sweep_runner_returns_all_expected_intersections() -> None:
    intersections = find_intersections(
        [
            Segment2D(Point2D(0, 0), Point2D(2, 2)),
            Segment2D(Point2D(0, 2), Point2D(2, 0)),
            Segment2D(Point2D(3, 0), Point2D(3, 2)),
        ]
    )

    assert len(intersections) == 1
    assert intersections[0].point == Point2D(1, 1)


def test_delaunay_triangulates_square_and_rejects_collinear_input() -> None:
    square = [Point2D(0, 0), Point2D(1, 0), Point2D(1, 1), Point2D(0, 1)]

    triangles = triangulate(square)

    assert len(triangles) == 2
    assert triangulate([Point2D(0, 0), Point2D(1, 0), Point2D(2, 0)]) == []
    assert all(triangle_circumcenter(triangle) == Point2D(0.5, 0.5) for triangle in triangles)


def test_voronoi_uses_delaunay_circumcenters() -> None:
    cells = build_voronoi([Point2D(0, 0), Point2D(1, 0), Point2D(0, 1)])

    assert len(cells) == 3
    assert all(cell.vertices == (Point2D(0.5, 0.5),) for cell in cells)


def test_input_loader_reads_csv_and_json_fixtures() -> None:
    csv_points = load_points("data/fixtures/default_points.csv")
    json_points = load_points("data/fixtures/default_points.json")

    assert csv_points == json_points
    assert len(csv_points) == 6


def test_input_loader_rejects_unknown_format(tmp_path: Path) -> None:
    source = tmp_path / "points.txt"
    source.write_text("0,0", encoding="utf-8")

    with pytest.raises(ValueError, match="unsupported input format"):
        load_points(source)


def test_input_loader_rejects_invalid_and_oversized_inputs(tmp_path: Path) -> None:
    bad_csv = tmp_path / "bad.csv"
    bad_csv.write_text("x,y\n1,nope\n", encoding="utf-8")
    empty_json = tmp_path / "empty.json"
    empty_json.write_text("[]", encoding="utf-8")

    with pytest.raises(ValueError, match="non-numeric"):
        load_points(bad_csv)
    with pytest.raises(ValueError, match="at least one point"):
        load_points(empty_json)
    with pytest.raises(ValueError, match="maximum is 1"):
        load_points("data/fixtures/default_points.csv", max_points=1)


def test_models_reject_invalid_geometry() -> None:
    with pytest.raises(ValueError, match="finite"):
        Point2D(inf, 0)
    with pytest.raises(ValueError, match="distinct"):
        Segment2D(Point2D(0, 0), Point2D(0, 0))
    with pytest.raises(ValueError, match="at least three"):
        Polygon2D([Point2D(0, 0), Point2D(1, 0)])
    with pytest.raises(ValueError, match="must not contain None"):
        Polygon2D([Point2D(0, 0), Point2D(1, 0), None])  # type: ignore[list-item]


def test_python_cli_runs_end_to_end() -> None:
    env = {**os.environ, "PYTHONPATH": "src/python"}
    coverage_keys = (
        "COVERAGE_PROCESS_START",
        "COV_CORE_SOURCE",
        "COV_CORE_CONFIG",
        "COV_CORE_DATAFILE",
    )
    for key in coverage_keys:
        env.pop(key, None)

    result = subprocess.run(
        [
            sys.executable,
            "-m",
            "geometry.cli.main",
            "--algorithm",
            "hull",
            "--input",
            "data/fixtures/default_points.csv",
        ],
        check=True,
        text=True,
        capture_output=True,
        env=env,
    )

    assert "Computational Geometry Demo - Python" in result.stdout
    assert "Convex hull (5)" in result.stdout
