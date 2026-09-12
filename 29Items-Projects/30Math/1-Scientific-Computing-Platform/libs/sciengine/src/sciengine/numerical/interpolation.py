"""Interpolation: Lagrange polynomials (teaching) and cubic splines (production)."""

from __future__ import annotations

from collections.abc import Callable, Sequence
from dataclasses import dataclass

import numpy as np

MAX_LAGRANGE_POINTS = 30  # global polynomial interpolation is ill-conditioned beyond this


@dataclass(frozen=True)
class LagrangeResult:
    """Interpolating polynomial through given points.

    ``evaluate`` uses the numerically stable barycentric form; ``coefficients``
    (ascending powers) exist so notebooks can display the algebraic polynomial.
    """

    coefficients: list[float]
    evaluate: Callable[[np.ndarray | float], np.ndarray]
    kind: str = "lagrange"


@dataclass(frozen=True)
class SplineResult:
    knots_x: list[float]
    knots_y: list[float]
    bc_type: str
    evaluate: Callable[[np.ndarray | float], np.ndarray]
    kind: str = "cubic_spline"


def lagrange_polynomial(x: Sequence[float], y: Sequence[float]) -> LagrangeResult:
    """Interpolating polynomial through the points (x_i, y_i).

    Evaluation goes through ``BarycentricInterpolator`` (stable); the explicit
    coefficient vector is recovered with an exact-degree least-squares fit and
    is only trustworthy for the small point counts we allow.
    """
    from scipy.interpolate import BarycentricInterpolator

    x_arr, y_arr = _validated_points(x, y, max_points=MAX_LAGRANGE_POINTS)
    interpolator = BarycentricInterpolator(x_arr, y_arr)
    coefficients = np.polynomial.polynomial.polyfit(x_arr, y_arr, deg=len(x_arr) - 1)

    def evaluate(points: np.ndarray | float) -> np.ndarray:
        return np.asarray(interpolator(np.asarray(points, dtype=float)), dtype=float)

    return LagrangeResult(
        coefficients=[float(c) for c in coefficients],
        evaluate=evaluate,
    )


def cubic_spline(
    x: Sequence[float], y: Sequence[float], *, bc_type: str = "natural"
) -> SplineResult:
    """Cubic spline through the knots. ``bc_type``: natural | clamped | not-a-knot."""
    from scipy.interpolate import CubicSpline

    if bc_type not in {"natural", "clamped", "not-a-knot"}:
        raise ValueError(f"Unsupported boundary condition: {bc_type!r}")
    x_arr, y_arr = _validated_points(x, y, max_points=10_000)
    if len(x_arr) < 3:
        raise ValueError("A cubic spline needs at least 3 points.")
    spline = CubicSpline(x_arr, y_arr, bc_type=bc_type)

    def evaluate(points: np.ndarray | float) -> np.ndarray:
        return np.asarray(spline(np.asarray(points, dtype=float)), dtype=float)

    return SplineResult(
        knots_x=[float(v) for v in x_arr],
        knots_y=[float(v) for v in y_arr],
        bc_type=bc_type,
        evaluate=evaluate,
    )


def _validated_points(
    x: Sequence[float], y: Sequence[float], *, max_points: int
) -> tuple[np.ndarray, np.ndarray]:
    x_arr = np.asarray(x, dtype=float)
    y_arr = np.asarray(y, dtype=float)
    if x_arr.ndim != 1 or x_arr.shape != y_arr.shape:
        raise ValueError("x and y must be 1-D sequences of equal length.")
    if len(x_arr) < 2:
        raise ValueError("At least 2 points are required.")
    if len(x_arr) > max_points:
        raise ValueError(f"At most {max_points} points are supported.")
    if len(np.unique(x_arr)) != len(x_arr):
        raise ValueError("x values must be distinct.")
    if not (np.all(np.isfinite(x_arr)) and np.all(np.isfinite(y_arr))):
        raise ValueError("All points must be finite.")
    order = np.argsort(x_arr)
    return x_arr[order], y_arr[order]
