"""Numerical quadrature: composite rules (teaching) + adaptive (production)."""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from typing import Literal

import numpy as np
from scipy import integrate as sp_integrate

from sciengine.exceptions import ComputationError

Method = Literal["adaptive", "trapezoid", "simpson"]


@dataclass(frozen=True)
class IntegrationResult:
    value: float
    error_estimate: float
    method: Method
    n_evaluations: int


def integrate_function(
    f: Callable[[float], float],
    a: float,
    b: float,
    *,
    method: Method = "adaptive",
    n: int = 1000,
) -> IntegrationResult:
    """Integrate ``f`` over ``[a, b]``.

    ``trapezoid`` / ``simpson`` are the composite classroom rules on ``n``
    subintervals (finite bounds only); ``adaptive`` delegates to QUADPACK
    (``scipy.integrate.quad``), supports improper integrals (±inf bounds),
    and is what the API uses by default.
    """
    if method == "adaptive":
        # quad handles ±np.inf bounds natively (variable-substitution rules).
        value, abserr = sp_integrate.quad(f, a, b, limit=200)
        if not np.isfinite(value):
            raise ComputationError("Integral did not converge to a finite value.")
        return IntegrationResult(float(value), float(abserr), "adaptive", -1)

    if not (np.isfinite(a) and np.isfinite(b)):
        raise ComputationError(
            "Composite rules need finite bounds; use method='adaptive' for improper integrals."
        )
    if n < 1:
        raise ValueError("n must be >= 1")
    if method == "simpson":
        # Simpson needs even n; the Richardson error estimate below also needs
        # the half-resolution grid to be Simpson-valid, hence a multiple of 4.
        n += (-n) % 4

    x = np.linspace(a, b, n + 1)
    y = np.array([f(xi) for xi in x], dtype=float)
    if not np.all(np.isfinite(y)):
        raise ComputationError("Integrand evaluated to a non-finite value inside [a, b].")

    if method == "trapezoid":
        value = float(np.trapezoid(y, x))
        coarse = float(np.trapezoid(y[::2], x[::2]))
        error = abs(value - coarse) / 3.0  # Richardson: O(h^2) rule
    elif method == "simpson":
        value = float(sp_integrate.simpson(y, x=x))
        coarse = float(sp_integrate.simpson(y[::2], x=x[::2]))
        error = abs(value - coarse) / 15.0  # Richardson: O(h^4) rule
    else:  # pragma: no cover - typing guards this
        raise ValueError(f"Unknown method: {method}")

    return IntegrationResult(value, error, method, n + 1)
