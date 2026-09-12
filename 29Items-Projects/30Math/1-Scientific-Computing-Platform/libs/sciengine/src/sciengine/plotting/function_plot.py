"""Server-side function plotting.

Rules for plotting anywhere outside a notebook (see docs/TECH-NOTES.md §3.6):
- Agg backend only (no display on servers),
- the object-oriented ``Figure`` API only — ``pyplot`` keeps global state,
  leaks figures, and is not thread-safe under FastAPI's threadpool.
"""

from __future__ import annotations

import io
from collections.abc import Callable
from typing import Literal

import matplotlib

matplotlib.use("Agg")  # must run before any pyplot import anywhere in-process

import numpy as np
from matplotlib.figure import Figure

from sciengine.exceptions import ComputationError
from sciengine.symbolic.parsing import parse_expression

ImageFormat = Literal["svg", "png"]


def plot_callable(
    f: Callable[[np.ndarray], np.ndarray],
    x_min: float,
    x_max: float,
    *,
    n_points: int = 1000,
    title: str | None = None,
    label: str | None = None,
    fmt: ImageFormat = "svg",
) -> bytes:
    """Plot a vectorized callable over [x_min, x_max]; return image bytes."""
    if not (np.isfinite(x_min) and np.isfinite(x_max)) or x_min >= x_max:
        raise ValueError(f"Invalid range: [{x_min}, {x_max}]")
    n_points = int(np.clip(n_points, 2, 5000))

    x = np.linspace(x_min, x_max, n_points)
    with np.errstate(all="ignore"):  # poles/domain gaps become NaN, not warnings
        y = np.asarray(f(x), dtype=float)
    y = np.where(np.isfinite(y), y, np.nan)  # matplotlib breaks the line at NaN
    if np.all(np.isnan(y)):
        raise ComputationError("Function is undefined everywhere on the requested range.")

    fig = Figure(figsize=(8, 4.5), dpi=110)
    ax = fig.subplots()
    ax.plot(x, y, linewidth=1.8, label=label)
    ax.axhline(0.0, linewidth=0.6, alpha=0.4)
    ax.grid(True, alpha=0.3)
    if title:
        ax.set_title(title)
    if label:
        ax.legend(loc="best")
    ax.set_xlabel("x")

    buf = io.BytesIO()
    fig.savefig(buf, format=fmt, bbox_inches="tight")
    return buf.getvalue()


def plot_sympy_expression(
    expression_text: str,
    variable: str = "x",
    x_min: float = -10.0,
    x_max: float = 10.0,
    *,
    n_points: int = 1000,
    fmt: ImageFormat = "svg",
) -> bytes:
    """Safe-parse a user expression and plot it.

    ``lambdify`` generates code from the *already-sanitized* SymPy tree (never
    from the raw user string), which keeps this path inside the parse_expression
    security boundary.
    """
    import sympy as sp

    expr = parse_expression(expression_text, allowed_symbols={variable})
    f = sp.lambdify(sp.Symbol(variable), expr, modules="numpy")
    latex_label = f"${sp.latex(expr)}$"
    return plot_callable(f, x_min, x_max, n_points=n_points, label=latex_label, fmt=fmt)
