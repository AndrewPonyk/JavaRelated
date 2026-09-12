"""Initial value problem (IVP) solvers.

Production path wraps ``scipy.integrate.solve_ivp`` (with a stiffness
fallback); ``explicit_euler`` and ``rk4`` are hand-rolled teaching steppers
that notebooks walk through line by line.
"""

from __future__ import annotations

from collections.abc import Callable, Sequence
from dataclasses import dataclass

import numpy as np

from sciengine.exceptions import ComputationError

RHS = Callable[[float, np.ndarray], Sequence[float] | np.ndarray | float]


@dataclass(frozen=True)
class ODEResult:
    t: np.ndarray
    y: np.ndarray  # shape (n_states, len(t))
    method: str
    success: bool
    message: str


def solve_ivp(
    rhs: RHS,
    t_span: tuple[float, float],
    y0: Sequence[float],
    *,
    method: str = "RK45",
    max_step: float | None = None,
    n_points: int = 200,
    rtol: float = 1e-6,
    atol: float = 1e-9,
) -> ODEResult:
    """Solve dy/dt = rhs(t, y) over ``t_span`` from ``y0`` on a uniform output grid.

    Tolerances default far tighter than SciPy's (1e-3) because students
    compare results against closed-form solutions. Falls back to the implicit
    ``Radau`` method when the explicit solver fails (the usual symptom of
    stiffness in classroom problems).
    """
    from scipy.integrate import solve_ivp as scipy_solve_ivp

    t0, t1 = float(t_span[0]), float(t_span[1])
    if not (np.isfinite(t0) and np.isfinite(t1)) or t0 >= t1:
        raise ValueError(f"Invalid time span: [{t0}, {t1}]")
    n_points = int(np.clip(n_points, 2, 5000))
    t_eval = np.linspace(t0, t1, n_points)
    y0_arr = np.atleast_1d(np.asarray(y0, dtype=float))

    kwargs: dict = {"t_eval": t_eval, "rtol": rtol, "atol": atol}
    if max_step is not None:
        kwargs["max_step"] = max_step

    solution = scipy_solve_ivp(rhs, (t0, t1), y0_arr, method=method, **kwargs)
    method_used = method
    if not solution.success and method != "Radau":
        solution = scipy_solve_ivp(rhs, (t0, t1), y0_arr, method="Radau", **kwargs)
        method_used = "Radau"
    if not solution.success:
        raise ComputationError(f"ODE solver failed: {solution.message}")

    return ODEResult(
        t=solution.t,
        y=solution.y,
        method=method_used,
        success=True,
        message=str(solution.message),
    )


def solve_ivp_expression(
    expression_text: str,
    t_span: tuple[float, float],
    y0: float,
    *,
    n_points: int = 200,
) -> dict:
    """Solve the scalar first-order IVP dy/dt = f(t, y) given as text.

    The expression may use symbols ``t`` and ``y`` only; it is parsed through
    the safe parser, so this function is registered in the sandbox
    (``sciengine.runtime``) and callable from workers with untrusted input.
    Returns a JSON-safe dict (lists, floats) ready for a result payload.
    """
    import sympy as sp

    from sciengine.symbolic.parsing import parse_expression

    expr = parse_expression(expression_text, allowed_symbols={"t", "y"})
    t_sym, y_sym = sp.Symbol("t"), sp.Symbol("y")
    # lambdify generates code from the already-sanitized tree, never raw text.
    f = sp.lambdify((t_sym, y_sym), expr, modules="numpy")

    def rhs(t: float, y: np.ndarray) -> np.ndarray:
        return np.atleast_1d(np.asarray(f(t, y[0]), dtype=float))

    result = solve_ivp(rhs, (float(t_span[0]), float(t_span[1])), [float(y0)], n_points=n_points)
    return {
        "t": [float(v) for v in result.t],
        "y": [float(v) for v in result.y[0]],
        "method": result.method,
        "success": result.success,
        "message": result.message,
    }


def explicit_euler(
    rhs: RHS, t_span: tuple[float, float], y0: Sequence[float], *, h: float
) -> ODEResult:
    """Forward Euler with fixed step ``h`` — the first method every course teaches."""
    t_values, y_values = _fixed_step_grid(t_span, y0, h)
    for i in range(len(t_values) - 1):
        y_values[:, i + 1] = y_values[:, i] + h * np.asarray(
            rhs(t_values[i], y_values[:, i]), dtype=float
        )
    return ODEResult(t_values, y_values, "euler", True, "fixed-step explicit Euler")


def rk4(rhs: RHS, t_span: tuple[float, float], y0: Sequence[float], *, h: float) -> ODEResult:
    """Classic 4th-order Runge–Kutta with fixed step ``h``."""
    t_values, y_values = _fixed_step_grid(t_span, y0, h)
    for i in range(len(t_values) - 1):
        t, y = t_values[i], y_values[:, i]
        k1 = np.asarray(rhs(t, y), dtype=float)
        k2 = np.asarray(rhs(t + h / 2, y + h * k1 / 2), dtype=float)
        k3 = np.asarray(rhs(t + h / 2, y + h * k2 / 2), dtype=float)
        k4 = np.asarray(rhs(t + h, y + h * k3), dtype=float)
        y_values[:, i + 1] = y + (h / 6.0) * (k1 + 2 * k2 + 2 * k3 + k4)
    return ODEResult(t_values, y_values, "rk4", True, "fixed-step classic RK4")


def _fixed_step_grid(
    t_span: tuple[float, float], y0: Sequence[float], h: float
) -> tuple[np.ndarray, np.ndarray]:
    t0, t1 = float(t_span[0]), float(t_span[1])
    if h <= 0:
        raise ValueError("Step size h must be positive.")
    if t0 >= t1:
        raise ValueError(f"Invalid time span: [{t0}, {t1}]")
    n_steps = int(np.ceil((t1 - t0) / h))
    if n_steps > 1_000_000:
        raise ValueError("Step size is too small for this interval (over 1e6 steps).")
    t_values = t0 + h * np.arange(n_steps + 1)
    t_values[-1] = min(t_values[-1], t1)
    y0_arr = np.atleast_1d(np.asarray(y0, dtype=float))
    y_values = np.zeros((y0_arr.size, n_steps + 1))
    y_values[:, 0] = y0_arr
    return t_values, y_values
