from __future__ import annotations

import csv
import json
from pathlib import Path
from typing import Any

from geometry.model.point import Point2D

DEFAULT_MAX_POINTS = 1_000_000


def load_points(path: str | Path, max_points: int = DEFAULT_MAX_POINTS) -> list[Point2D]:
    source = Path(path)
    if not source.exists():
        raise FileNotFoundError(f"input file does not exist: {source}")
    if source.suffix.lower() == ".csv":
        return _load_csv(source, max_points)
    if source.suffix.lower() == ".json":
        return _load_json(source, max_points)
    raise ValueError(f"unsupported input format: {source.suffix}; expected .csv or .json")


def _load_csv(path: Path, max_points: int) -> list[Point2D]:
    points: list[Point2D] = []
    with path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        if "x" not in (reader.fieldnames or []) or "y" not in (reader.fieldnames or []):
            raise ValueError("CSV input must include x and y columns")
        for row_number, row in enumerate(reader, start=2):
            points.append(_point_from_mapping(row, f"CSV row {row_number}"))
    return _validate_points(points, max_points)


def _load_json(path: Path, max_points: int) -> list[Point2D]:
    with path.open(encoding="utf-8") as handle:
        payload = json.load(handle)
    if not isinstance(payload, list):
        raise ValueError("JSON input must be an array of objects with x and y fields")
    points = [_point_from_mapping(item, f"JSON item {index}") for index, item in enumerate(payload)]
    return _validate_points(points, max_points)


def _point_from_mapping(value: Any, label: str) -> Point2D:
    if not isinstance(value, dict) or "x" not in value or "y" not in value:
        raise ValueError(f"{label} must contain x and y")
    try:
        return Point2D(float(value["x"]), float(value["y"]))
    except (TypeError, ValueError) as exc:
        raise ValueError(f"{label} has non-numeric coordinates") from exc


def _validate_points(points: list[Point2D], max_points: int) -> list[Point2D]:
    if not points:
        raise ValueError("input must contain at least one point")
    if len(points) > max_points:
        raise ValueError(f"input contains {len(points)} points; maximum is {max_points}")
    return points
