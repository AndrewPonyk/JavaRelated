"""Headless plotting tests: SVG/PNG bytes out, poles handled, ranges validated."""

import numpy as np
import pytest

from sciengine.exceptions import ComputationError, ExpressionParseError
from sciengine.plotting.function_plot import plot_callable, plot_sympy_expression


def test_plot_callable_produces_svg():
    data = plot_callable(np.sin, -3.0, 3.0, title="sine", label="sin(x)")
    assert data.startswith(b"<?xml") or b"<svg" in data[:500]


def test_plot_callable_png_format():
    data = plot_callable(np.cos, 0.0, 1.0, fmt="png", n_points=50)
    assert data[:8] == b"\x89PNG\r\n\x1a\n"


def test_poles_become_gaps_not_errors():
    # 1/x has a pole at 0; non-finite samples must turn into line gaps.
    data = plot_callable(lambda x: 1.0 / x, -1.0, 1.0, n_points=101)
    assert b"<svg" in data[:500] or data.startswith(b"<?xml")


def test_invalid_range_rejected():
    with pytest.raises(ValueError, match="Invalid range"):
        plot_callable(np.sin, 2.0, -2.0)
    with pytest.raises(ValueError, match="Invalid range"):
        plot_callable(np.sin, 0.0, np.inf)


def test_everywhere_undefined_function_rejected():
    with pytest.raises(ComputationError, match="undefined everywhere"):
        plot_callable(lambda x: np.full_like(x, np.nan), 0.0, 1.0)


def test_plot_sympy_expression_end_to_end():
    data = plot_sympy_expression("sin(x) + sin(3x)/3", "x", -6.0, 6.0)
    assert b"<svg" in data[:500] or data.startswith(b"<?xml")


def test_plot_sympy_expression_is_parser_guarded():
    with pytest.raises(ExpressionParseError):
        plot_sympy_expression("__import__('os')", "x")
    with pytest.raises(ExpressionParseError, match="Unknown symbol"):
        plot_sympy_expression("x + y", "x")


def test_n_points_is_clamped():
    # Requests beyond the cap must not blow up memory — they are clamped.
    data = plot_callable(np.sin, 0.0, 1.0, n_points=999_999)
    assert data
