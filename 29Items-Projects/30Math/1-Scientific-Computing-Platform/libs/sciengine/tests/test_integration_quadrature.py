"""Quadrature tests: golden analytic values + the property-based invariant
promised in docs/TECH-NOTES.md §3.2.
"""

import math

import numpy as np
import pytest
from hypothesis import given
from hypothesis import strategies as st

from sciengine.exceptions import ComputationError
from sciengine.numerical.integration import integrate_function


def test_adaptive_integrates_sin_over_half_period():
    result = integrate_function(math.sin, 0.0, math.pi)
    assert math.isclose(result.value, 2.0, rel_tol=1e-10)
    assert result.method == "adaptive"


def test_adaptive_handles_improper_integral():
    result = integrate_function(lambda t: math.exp(-t), 0.0, np.inf)
    assert math.isclose(result.value, 1.0, rel_tol=1e-9)


def test_composite_rules_reject_infinite_bounds():
    with pytest.raises(ComputationError, match="finite bounds"):
        integrate_function(math.exp, 0.0, np.inf, method="simpson")


def test_trapezoid_rule_with_error_estimate():
    result = integrate_function(math.sin, 0.0, math.pi, method="trapezoid", n=200)
    assert math.isclose(result.value, 2.0, rel_tol=1e-3)
    assert math.isfinite(result.error_estimate)
    assert result.n_evaluations == 201


def test_n_must_be_positive():
    with pytest.raises(ValueError, match="n must be"):
        integrate_function(math.sin, 0.0, 1.0, method="trapezoid", n=0)


def test_simpson_error_estimate_is_finite_and_small():
    result = integrate_function(math.sin, 0.0, math.pi, method="simpson", n=64)
    assert math.isclose(result.value, 2.0, rel_tol=1e-6)
    assert math.isfinite(result.error_estimate)
    assert result.error_estimate < 1e-6


def test_non_finite_integrand_rejected():
    with pytest.raises(ComputationError, match="non-finite"):
        integrate_function(lambda t: 1.0 / t, -1.0, 1.0, method="trapezoid", n=10)


@given(st.floats(-10, 10), st.floats(-10, 10))
def test_simpson_is_exact_for_linear_functions(a, b):
    lo, hi = sorted((a, b))
    result = integrate_function(lambda t: 3 * t + 1, lo, hi, method="simpson", n=64)
    exact = (1.5 * hi**2 + hi) - (1.5 * lo**2 + lo)
    assert math.isclose(result.value, exact, rel_tol=1e-9, abs_tol=1e-9)


@given(st.integers(0, 3))
def test_simpson_is_exact_for_cubics(degree):
    # Simpson's rule integrates polynomials up to degree 3 exactly.
    result = integrate_function(lambda t: t**degree, 0.0, 2.0, method="simpson", n=16)
    exact = 2.0 ** (degree + 1) / (degree + 1)
    assert math.isclose(result.value, exact, rel_tol=1e-12, abs_tol=1e-12)
