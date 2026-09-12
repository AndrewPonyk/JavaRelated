"""Symbolic calculus: differentiation, integration, limits, series."""

from __future__ import annotations

from dataclasses import dataclass

import sympy as sp

from sciengine.exceptions import ComputationError, UnsupportedExpressionError
from sciengine.symbolic.parsing import parse_expression

MAX_DERIVATIVE_ORDER = 10
MAX_SERIES_ORDER = 12


@dataclass(frozen=True)
class CalculusResult:
    input_latex: str
    result_text: str
    result_latex: str
    operation: str


def differentiate(expression_text: str, variable: str = "x", order: int = 1) -> CalculusResult:
    """d^order/d(variable)^order of the given expression."""
    if not 1 <= order <= MAX_DERIVATIVE_ORDER:
        raise UnsupportedExpressionError(
            f"Derivative order must be between 1 and {MAX_DERIVATIVE_ORDER}."
        )
    expr = parse_expression(expression_text)
    var = sp.Symbol(variable)
    try:
        result = sp.diff(expr, var, order)
    except Exception as exc:
        raise ComputationError(f"Differentiation failed: {exc}") from exc
    return CalculusResult(sp.latex(expr), sp.sstr(result), sp.latex(result), "differentiate")


def integrate_symbolic(expression_text: str, variable: str = "x") -> CalculusResult:
    """Antiderivative (indefinite integral). May legitimately return an
    unevaluated Integral — the UI presents that as "no elementary form".

    NOTE: unbounded runtime — callers run this through sciengine.runtime.
    """
    expr = parse_expression(expression_text)
    var = sp.Symbol(variable)
    try:
        result = sp.integrate(expr, var)
    except Exception as exc:
        raise ComputationError(f"Integration failed: {exc}") from exc
    return CalculusResult(sp.latex(expr), sp.sstr(result), sp.latex(result), "integrate")


def limit(
    expression_text: str,
    variable: str = "x",
    to: str = "0",
    direction: str = "+",
) -> CalculusResult:
    """lim_{variable -> to} expression. ``to`` accepts expressions incl. ``oo``."""
    if direction not in {"+", "-"}:
        raise UnsupportedExpressionError("Limit direction must be '+' or '-'.")
    expr = parse_expression(expression_text)
    var = sp.Symbol(variable)
    point = parse_expression(to)
    try:
        result = sp.limit(expr, var, point, dir=direction)
    except NotImplementedError as exc:
        raise UnsupportedExpressionError(f"This limit cannot be computed yet: {exc}") from exc
    except Exception as exc:
        raise ComputationError(f"Limit computation failed: {exc}") from exc
    input_latex = rf"\lim_{{{sp.latex(var)} \to {sp.latex(point)}^{direction}}} {sp.latex(expr)}"
    return CalculusResult(input_latex, sp.sstr(result), sp.latex(result), "limit")


def taylor_series(
    expression_text: str,
    variable: str = "x",
    around: str = "0",
    order: int = 6,
) -> CalculusResult:
    """Taylor polynomial of the given order around a point (big-O term removed)."""
    if not 1 <= order <= MAX_SERIES_ORDER:
        raise UnsupportedExpressionError(f"Series order must be between 1 and {MAX_SERIES_ORDER}.")
    expr = parse_expression(expression_text)
    var = sp.Symbol(variable)
    point = parse_expression(around)
    try:
        result = sp.series(expr, var, point, order).removeO()
    except NotImplementedError as exc:
        raise UnsupportedExpressionError(f"No series expansion is available here: {exc}") from exc
    except Exception as exc:
        raise ComputationError(f"Series expansion failed: {exc}") from exc
    return CalculusResult(sp.latex(expr), sp.sstr(result), sp.latex(result), "series")
