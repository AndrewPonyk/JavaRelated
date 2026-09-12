"""Symbolic equation solving with API-friendly result objects."""

from __future__ import annotations

from dataclasses import dataclass, field

import sympy as sp

from sciengine.exceptions import ComputationError, UnsupportedExpressionError
from sciengine.symbolic.parsing import parse_equation
from sciengine.symbolic.steps import solve_steps


@dataclass(frozen=True)
class SolveResult:
    """JSON-serializable outcome of a symbolic solve."""

    equation_latex: str
    variable: str
    solutions: list[str]  # canonical text form (round-trips through the parser)
    solutions_latex: list[str]
    steps_latex: list[str] = field(default_factory=list)


@dataclass(frozen=True)
class SystemSolveResult:
    """JSON-serializable outcome of solving a system of equations."""

    equations_latex: list[str]
    variables: list[str]
    solutions: list[dict[str, str]]  # one dict per solution: {var: value}
    solutions_latex: list[dict[str, str]]


def solve_equation(equation_text: str, variable: str = "x") -> SolveResult:
    """Solve ``equation_text`` (e.g. ``"x^2 - 4 = 0"``) for ``variable``.

    NOTE: SymPy's ``solve`` can run unboundedly long on adversarial input.
    Callers own the time budget: everything that reaches this function from
    the API or workers goes through ``sciengine.runtime`` (killable subprocess
    with a hard timeout — docs/ARCHITECTURE.md §2.4).
    """
    equation = parse_equation(equation_text)
    var = sp.Symbol(variable)
    if var not in equation.free_symbols:
        raise UnsupportedExpressionError(f"Variable '{variable}' does not appear in the equation.")

    try:
        solutions = sp.solve(equation, var)
    except NotImplementedError as exc:
        raise UnsupportedExpressionError(
            f"No symbolic solver is available for this equation: {exc}"
        ) from exc
    except Exception as exc:
        raise ComputationError(f"Solver failed: {exc}") from exc

    if not isinstance(solutions, list):  # sympy occasionally returns a dict/bool
        raise UnsupportedExpressionError(
            "Equation produced a non-standard solution set; not supported yet."
        )

    return SolveResult(
        equation_latex=sp.latex(equation),
        variable=variable,
        solutions=[sp.sstr(s) for s in solutions],
        solutions_latex=[sp.latex(s) for s in solutions],
        steps_latex=solve_steps(equation, var, solutions),
    )


def solve_system(equations: list[str], variables: list[str]) -> SystemSolveResult:
    """Solve a system of equations for multiple variables.

    Systems are combinatorially explosive; API routing always sends them to
    the worker queue with the full sandbox budget.
    """
    if not 1 <= len(equations) <= 8:
        raise UnsupportedExpressionError("Between 1 and 8 equations are supported.")
    if not 1 <= len(variables) <= 8:
        raise UnsupportedExpressionError("Between 1 and 8 variables are supported.")
    if len(set(variables)) != len(variables):
        raise UnsupportedExpressionError("Variables must be distinct.")

    parsed = [parse_equation(text) for text in equations]
    symbols = [sp.Symbol(name) for name in variables]
    present = set().union(*(eq.free_symbols for eq in parsed))
    missing = [s.name for s in symbols if s not in present]
    if missing:
        raise UnsupportedExpressionError(
            f"Variable(s) {', '.join(missing)} do not appear in the system."
        )

    try:
        raw_solutions = sp.solve(parsed, symbols, dict=True)
    except NotImplementedError as exc:
        raise UnsupportedExpressionError(
            f"No symbolic solver is available for this system: {exc}"
        ) from exc
    except Exception as exc:
        raise ComputationError(f"System solver failed: {exc}") from exc

    return SystemSolveResult(
        equations_latex=[sp.latex(eq) for eq in parsed],
        variables=variables,
        solutions=[{str(k): sp.sstr(v) for k, v in solution.items()} for solution in raw_solutions],
        solutions_latex=[
            {str(k): sp.latex(v) for k, v in solution.items()} for solution in raw_solutions
        ],
    )
