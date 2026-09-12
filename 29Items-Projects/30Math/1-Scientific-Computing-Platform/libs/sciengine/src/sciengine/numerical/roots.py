"""Root finding for scalar real functions.

``bisect`` and ``newton`` are implemented by hand — they are the canonical
teaching algorithms of the platform and notebooks step through them (pass
``trace=True`` to capture per-iteration history). ``brent`` wraps SciPy's
production-grade solver behind the same result contract.
"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass

from sciengine.exceptions import ConvergenceError

Func = Callable[[float], float]

Trace = tuple[tuple[float, float], ...]  # ((x, f(x)), ...) per iteration


@dataclass(frozen=True)
class RootResult:
    root: float
    iterations: int
    residual: float
    method: str
    trace: Trace | None = None


def bisect(
    f: Func,
    a: float,
    b: float,
    *,
    tol: float = 1e-12,
    max_iter: int = 200,
    trace: bool = False,
) -> RootResult:
    """Find a root of ``f`` on ``[a, b]`` by bisection.

    Requires a sign change: ``f(a) * f(b) < 0``. Linear convergence,
    unconditionally robust — the pedagogical baseline every other method
    is compared against.
    """
    fa, fb = f(a), f(b)
    if fa == 0.0:
        return RootResult(a, 0, 0.0, "bisect", () if trace else None)
    if fb == 0.0:
        return RootResult(b, 0, 0.0, "bisect", () if trace else None)
    if fa * fb > 0:
        raise ValueError(f"f(a) and f(b) must have opposite signs; got f({a})={fa}, f({b})={fb}")

    history: list[tuple[float, float]] = []
    lo, hi = (a, b) if a < b else (b, a)
    for iteration in range(1, max_iter + 1):
        mid = (lo + hi) / 2.0
        fmid = f(mid)
        if trace:
            history.append((mid, fmid))
        if fmid == 0.0 or (hi - lo) / 2.0 < tol:
            return RootResult(
                mid, iteration, abs(fmid), "bisect", tuple(history) if trace else None
            )
        if fa * fmid < 0:
            hi = mid
        else:
            lo, fa = mid, fmid
    raise ConvergenceError(
        f"Bisection did not reach tol={tol} within {max_iter} iterations.",
        iterations=max_iter,
        residual=abs(f((lo + hi) / 2.0)),
    )


def newton(
    f: Func,
    x0: float,
    *,
    fprime: Func | None = None,
    tol: float = 1e-12,
    max_iter: int = 50,
    trace: bool = False,
) -> RootResult:
    """Newton–Raphson iteration starting at ``x0``.

    Quadratic convergence near simple roots; may diverge otherwise (that is a
    feature for teaching!). Falls back to a central finite difference when no
    analytic derivative is supplied.
    """

    def numeric_derivative(x: float, h: float = 1e-7) -> float:
        return (f(x + h) - f(x - h)) / (2.0 * h)

    dfdx = fprime or numeric_derivative
    history: list[tuple[float, float]] = []
    x = x0
    for iteration in range(1, max_iter + 1):
        fx = f(x)
        if trace:
            history.append((x, fx))
        if abs(fx) < tol:
            return RootResult(
                x, iteration - 1, abs(fx), "newton", tuple(history) if trace else None
            )
        d = dfdx(x)
        if d == 0.0:
            raise ConvergenceError(
                f"Zero derivative at x={x!r}; Newton step undefined.",
                iterations=iteration,
                residual=abs(fx),
            )
        step = fx / d
        x -= step
        if abs(step) < tol:
            return RootResult(x, iteration, abs(f(x)), "newton", tuple(history) if trace else None)
    raise ConvergenceError(
        f"Newton did not converge within {max_iter} iterations (last x={x!r}).",
        iterations=max_iter,
        residual=abs(f(x)),
    )


def brent(
    f: Func,
    a: float,
    b: float,
    *,
    tol: float = 1e-12,
    max_iter: int = 100,
) -> RootResult:
    """Brent's method (inverse quadratic + bisection safeguard) via SciPy.

    The production choice: superlinear convergence with bisection's
    robustness. Requires a bracketing interval like ``bisect``.
    """
    from scipy import optimize

    try:
        root, info = optimize.brentq(f, a, b, xtol=tol, maxiter=max_iter, full_output=True)
    except ValueError as exc:  # scipy's "f(a) and f(b) must have different signs"
        raise ValueError(str(exc)) from exc
    if not info.converged:
        raise ConvergenceError(
            f"Brent did not converge within {max_iter} iterations.",
            iterations=info.iterations,
            residual=abs(f(root)),
        )
    return RootResult(float(root), int(info.iterations), abs(f(float(root))), "brent")
