"""ODE solver tests against closed-form solutions."""

import math

import numpy as np
import pytest

from sciengine.numerical.ode import explicit_euler, rk4, solve_ivp, solve_ivp_expression


def decay(t, y):
    return -y  # y' = -y, y(0)=1  =>  y(t) = e^{-t}


def test_solve_ivp_matches_exponential_decay():
    result = solve_ivp(decay, (0.0, 2.0), [1.0])
    assert result.success
    expected = np.exp(-result.t)
    assert np.allclose(result.y[0], expected, rtol=1e-4, atol=1e-6)


def test_solve_ivp_rejects_bad_span():
    with pytest.raises(ValueError, match="time span"):
        solve_ivp(decay, (2.0, 0.0), [1.0])


def test_rk4_beats_euler_at_same_step():
    h = 0.1
    euler_result = explicit_euler(decay, (0.0, 1.0), [1.0], h=h)
    rk4_result = rk4(decay, (0.0, 1.0), [1.0], h=h)
    exact = math.exp(-1.0)
    euler_error = abs(euler_result.y[0, -1] - exact)
    rk4_error = abs(rk4_result.y[0, -1] - exact)
    assert rk4_error < euler_error / 100  # 4th order vs 1st order


def test_fixed_step_validation():
    with pytest.raises(ValueError, match="positive"):
        rk4(decay, (0.0, 1.0), [1.0], h=-0.1)


def test_solve_ivp_expression_end_to_end():
    payload = solve_ivp_expression("-y", (0.0, 1.0), 1.0, n_points=50)
    assert payload["success"] is True
    assert len(payload["t"]) == len(payload["y"]) == 50
    assert math.isclose(payload["y"][-1], math.exp(-1.0), rel_tol=1e-3)


def test_solve_ivp_expression_rejects_foreign_symbols():
    from sciengine.exceptions import ExpressionParseError

    with pytest.raises(ExpressionParseError, match="Unknown symbol"):
        solve_ivp_expression("-y + z", (0.0, 1.0), 1.0)
