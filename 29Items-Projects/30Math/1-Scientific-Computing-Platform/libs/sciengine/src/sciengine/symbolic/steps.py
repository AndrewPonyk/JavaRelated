"""Step-by-step derivation rendering for solved equations.

Produces an ordered list of LaTeX lines showing *how* the solution was
reached. Polynomial equations of degree 1–2 get genuine method steps
(isolation, factoring, quadratic formula); everything else gets the honest
generic derivation (original → canonical form → solutions).
"""

from __future__ import annotations

import sympy as sp


def solve_steps(equation: sp.Eq, variable: sp.Symbol, solutions: list[sp.Expr]) -> list[str]:
    """LaTeX derivation for ``equation`` solved for ``variable``."""
    steps: list[str] = [sp.latex(equation)]

    canonical = sp.expand(equation.lhs - equation.rhs)
    if equation.rhs != 0 or canonical != equation.lhs:
        steps.append(f"{sp.latex(canonical)} = 0")

    poly = _as_polynomial(canonical, variable)
    if poly is not None and poly.degree() == 1:
        steps.extend(_linear_steps(poly, variable))
    elif poly is not None and poly.degree() == 2:
        steps.extend(_quadratic_steps(poly, canonical, variable))

    if solutions:
        steps.append(", \\; ".join(f"{sp.latex(variable)} = {sp.latex(s)}" for s in solutions))
    else:
        steps.append(r"\text{no solutions}")
    return steps


def _as_polynomial(expr: sp.Expr, variable: sp.Symbol) -> sp.Poly | None:
    if variable not in expr.free_symbols:
        return None
    try:
        return sp.Poly(expr, variable)
    except sp.PolynomialError:
        return None


def _linear_steps(poly: sp.Poly, variable: sp.Symbol) -> list[str]:
    a, b = poly.all_coeffs()
    steps = [f"{sp.latex(a * variable)} = {sp.latex(sp.expand(-b))}"]
    if a != 1:
        steps.append(
            f"{sp.latex(variable)} = " f"\\frac{{{sp.latex(sp.expand(-b))}}}{{{sp.latex(a)}}}"
        )
    return steps


def _quadratic_steps(poly: sp.Poly, canonical: sp.Expr, variable: sp.Symbol) -> list[str]:
    a, b, c = poly.all_coeffs()
    factored = sp.factor(canonical)
    if isinstance(factored, sp.Mul) and factored != canonical:
        # Factoring worked over the rationals — the classroom-preferred route.
        return [f"{sp.latex(factored)} = 0"]
    # Fall back to the quadratic formula with the actual coefficients shown.
    discriminant = sp.expand(b**2 - 4 * a * c)
    return [
        rf"\Delta = b^2 - 4ac = {sp.latex(discriminant)}",
        rf"{sp.latex(variable)} = \frac{{-\left({sp.latex(b)}\right)"
        rf" \pm \sqrt{{{sp.latex(discriminant)}}}}}{{2 \cdot {sp.latex(a)}}}",
    ]
