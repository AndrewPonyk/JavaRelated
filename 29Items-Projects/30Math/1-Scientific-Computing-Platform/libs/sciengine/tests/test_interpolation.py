"""Interpolation tests: exactness on polynomials, spline knot fidelity."""

import numpy as np
import pytest

from sciengine.numerical.interpolation import cubic_spline, lagrange_polynomial


def test_lagrange_reproduces_parabola_exactly():
    x = [-1.0, 0.0, 2.0]
    y = [xi**2 for xi in x]  # y = x^2 through 3 points → exact degree-2 recovery
    result = lagrange_polynomial(x, y)
    probe = np.linspace(-2, 3, 25)
    assert np.allclose(result.evaluate(probe), probe**2, rtol=1e-9, atol=1e-9)
    # ascending coefficients of x^2: [0, 0, 1]
    assert np.allclose(result.coefficients, [0.0, 0.0, 1.0], atol=1e-9)


def test_lagrange_rejects_duplicate_x():
    with pytest.raises(ValueError, match="distinct"):
        lagrange_polynomial([1.0, 1.0, 2.0], [0.0, 1.0, 2.0])


def test_lagrange_point_cap():
    xs = list(range(40))
    with pytest.raises(ValueError, match="At most"):
        lagrange_polynomial(xs, xs)


def test_cubic_spline_interpolates_knots():
    x = [0.0, 1.0, 2.0, 3.0]
    y = [0.0, 1.0, 0.0, 2.0]
    result = cubic_spline(x, y)
    assert np.allclose(result.evaluate(np.asarray(x)), y, atol=1e-12)
    assert result.bc_type == "natural"


def test_cubic_spline_bc_type_validated():
    with pytest.raises(ValueError, match="boundary condition"):
        cubic_spline([0, 1, 2], [0, 1, 0], bc_type="periodic-ish")
