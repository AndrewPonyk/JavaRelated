"""Golden analytic tests for root finding (docs/TECH-NOTES.md §3.2)."""

import math

import pytest
from hypothesis import given
from hypothesis import strategies as st

from sciengine.numerical.roots import bisect, newton


def test_bisect_finds_sqrt2():
    result = bisect(lambda x: x * x - 2.0, 0.0, 2.0)
    assert math.isclose(result.root, math.sqrt(2.0), rel_tol=1e-10)
    assert result.method == "bisect"
    assert result.iterations > 0


def test_bisect_requires_sign_change():
    with pytest.raises(ValueError, match="opposite signs"):
        bisect(lambda x: x * x + 1.0, -1.0, 1.0)


def test_bisect_exact_endpoint_root():
    result = bisect(lambda x: x, 0.0, 1.0)
    assert result.root == 0.0
    assert result.iterations == 0


def test_newton_converges_on_dottie_number():
    # Fixed point of cos: cos(x) = x  =>  f(x) = cos(x) - x
    result = newton(lambda x: math.cos(x) - x, 1.0)
    assert math.isclose(result.root, 0.7390851332151607, rel_tol=1e-9)


def test_newton_with_analytic_derivative():
    result = newton(lambda x: x * x - 2.0, 1.0, fprime=lambda x: 2.0 * x)
    assert math.isclose(result.root, math.sqrt(2.0), rel_tol=1e-12)


def test_brent_finds_sqrt2():
    from sciengine.numerical.roots import brent

    result = brent(lambda x: x * x - 2.0, 0.0, 2.0)
    assert math.isclose(result.root, math.sqrt(2.0), rel_tol=1e-10)
    assert result.method == "brent"
    assert result.iterations < 20  # superlinear: far fewer than bisection


def test_brent_requires_bracket():
    from sciengine.numerical.roots import brent

    with pytest.raises(ValueError):
        brent(lambda x: x * x + 1.0, -1.0, 1.0)


def test_bisect_convergence_error_carries_context():
    from sciengine.exceptions import ConvergenceError

    with pytest.raises(ConvergenceError) as excinfo:
        bisect(lambda x: x * x - 2.0, 0.0, 2.0, tol=1e-30, max_iter=3)
    assert excinfo.value.iterations == 3
    assert excinfo.value.residual is not None


def test_newton_zero_derivative_raises():
    from sciengine.exceptions import ConvergenceError

    with pytest.raises(ConvergenceError, match="Zero derivative"):
        newton(lambda x: x * x + 1.0, 0.0, fprime=lambda x: 2.0 * x)


def test_trace_capture_for_teaching():
    result = bisect(lambda x: x * x - 2.0, 0.0, 2.0, trace=True)
    assert result.trace is not None
    assert len(result.trace) == result.iterations
    # residuals shrink overall from first to last recorded iterate
    assert abs(result.trace[-1][1]) < abs(result.trace[0][1])

    newton_result = newton(lambda x: x * x - 2.0, 1.0, trace=True)
    assert newton_result.trace is not None and len(newton_result.trace) >= 1


@given(
    r1=st.floats(-5, 5, allow_nan=False, allow_infinity=False),
    gap=st.floats(0.5, 5, allow_nan=False, allow_infinity=False),
)
def test_bisect_recovers_a_root_of_any_monic_quadratic(r1, gap):
    # (x - r1)(x - r2) with r2 = r1 + gap: bracketing the smaller root.
    r2 = r1 + gap

    def f(x: float) -> float:
        return (x - r1) * (x - r2)

    midpoint = (r1 + r2) / 2.0  # strictly between the roots → f < 0
    result = bisect(f, r1 - 1.0, midpoint, tol=1e-10)
    assert math.isclose(result.root, r1, rel_tol=1e-6, abs_tol=1e-6)
