"""Solver contract tests: results must be JSON-ready and mathematically right."""

import pytest
import sympy as sp

from sciengine.exceptions import UnsupportedExpressionError
from sciengine.symbolic.solver import SolveResult, solve_equation


def test_solves_factorable_quadratic():
    result = solve_equation("x^2 - 4 = 0")
    assert isinstance(result, SolveResult)
    assert sorted(result.solutions) == ["-2", "2"]
    assert len(result.solutions_latex) == 2
    assert result.variable == "x"


def test_solves_with_implied_rhs_zero():
    result = solve_equation("x - 3")
    assert result.solutions == ["3"]


def test_solutions_round_trip_through_sympy():
    # Symbolic equivalence, not string equality (docs/TECH-NOTES.md §3.2).
    result = solve_equation("x^2 = 2")
    values = sorted(sp.sympify(s) for s in result.solutions)
    assert values == [-sp.sqrt(2), sp.sqrt(2)]


def test_missing_variable_is_rejected():
    with pytest.raises(UnsupportedExpressionError, match="does not appear"):
        solve_equation("y + 1 = 0", variable="x")


def test_latex_output_present():
    result = solve_equation("2x = 8")
    assert result.equation_latex  # e.g. "2 x = 8"
    assert result.solutions_latex == ["4"]


class TestDerivationSteps:
    def test_linear_steps_show_isolation(self):
        result = solve_equation("2x + 6 = 0")
        assert len(result.steps_latex) >= 3
        assert result.steps_latex[0]  # original equation
        assert any(
            "frac" in step or "= -6" in step.replace(" ", "= -6") for step in result.steps_latex
        )
        assert result.steps_latex[-1] == "x = -3"

    def test_factorable_quadratic_shows_factored_form(self):
        result = solve_equation("x^2 - 4 = 0")
        assert any(
            "x - 2" in step and "x + 2" in step for step in result.steps_latex
        ), result.steps_latex

    def test_irreducible_quadratic_shows_quadratic_formula(self):
        result = solve_equation("x^2 + x - 1 = 0")
        joined = " ".join(result.steps_latex)
        assert "\\Delta" in joined and "\\pm" in joined

    def test_non_polynomial_gets_generic_steps(self):
        result = solve_equation("sin(x) = 0")
        assert result.steps_latex[0]
        assert result.steps_latex[-1]  # solutions line

    def test_derivation_block_renderer(self):
        from sciengine.symbolic.latex import derivation_to_latex

        block = derivation_to_latex(["a = b", "b = c"])
        assert block.startswith("\\begin{aligned}")
        assert block.endswith("\\end{aligned}")
        assert derivation_to_latex([]) == ""

    def test_equation_to_latex_helper(self):
        from sciengine.symbolic.latex import equation_to_latex

        assert equation_to_latex("x^2 = 4") == "x^{2} = 4"


class TestSystems:
    def test_linear_system_two_unknowns(self):
        from sciengine.symbolic.solver import solve_system

        result = solve_system(["x + y = 3", "x - y = 1"], ["x", "y"])
        assert result.solutions == [{"x": "2", "y": "1"}]
        assert result.variables == ["x", "y"]
        assert len(result.equations_latex) == 2

    def test_system_variable_must_appear(self):
        from sciengine.symbolic.solver import solve_system

        with pytest.raises(UnsupportedExpressionError, match="do not appear"):
            solve_system(["x + 1 = 0"], ["x", "z"])

    def test_system_size_limits(self):
        from sciengine.symbolic.solver import solve_system

        with pytest.raises(UnsupportedExpressionError, match="Between 1 and 8"):
            solve_system(["x = 1"] * 9, ["x"])
