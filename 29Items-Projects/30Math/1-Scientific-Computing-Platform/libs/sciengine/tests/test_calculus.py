"""Golden analytic tests for symbolic calculus."""

import pytest
import sympy as sp

from sciengine.exceptions import UnsupportedExpressionError
from sciengine.symbolic.calculus import (
    differentiate,
    integrate_symbolic,
    limit,
    taylor_series,
)

x = sp.Symbol("x")


def _expr(result_text: str) -> sp.Expr:
    # Solver output is trusted (produced by sp.sstr); sympify is fine in tests.
    return sp.sympify(result_text)


def test_differentiate_power_rule():
    result = differentiate("x^3", "x")
    assert sp.simplify(_expr(result.result_text) - 3 * x**2) == 0
    assert result.operation == "differentiate"


def test_differentiate_higher_order():
    result = differentiate("sin(x)", "x", order=2)
    assert sp.simplify(_expr(result.result_text) + sp.sin(x)) == 0


def test_differentiate_order_bounds():
    with pytest.raises(UnsupportedExpressionError, match="order"):
        differentiate("x", "x", order=11)


def test_integrate_polynomial():
    result = integrate_symbolic("2x", "x")
    assert sp.simplify(_expr(result.result_text) - x**2) == 0


def test_integrate_nonelementary_returns_unevaluated():
    # exp(-x^2) has no elementary antiderivative; sympy returns erf-based form.
    result = integrate_symbolic("exp(-x^2)", "x")
    assert result.result_latex  # present, whatever closed form sympy chose


def test_limit_classic_sinc():
    result = limit("sin(x)/x", "x", to="0")
    assert _expr(result.result_text) == 1


def test_limit_at_infinity():
    result = limit("(2x + 1)/x", "x", to="oo")
    assert _expr(result.result_text) == 2


def test_limit_direction_validated():
    with pytest.raises(UnsupportedExpressionError, match="direction"):
        limit("1/x", "x", to="0", direction="up")


def test_taylor_series_of_exp():
    result = taylor_series("exp(x)", "x", around="0", order=4)
    expected = 1 + x + x**2 / 2 + x**3 / 6
    assert sp.simplify(_expr(result.result_text) - expected) == 0
