"""Regression suite for the platform's primary attack surface.

Every known bypass idea for the expression parser gets a test here — these are
security tests as much as correctness tests (docs/ARCHITECTURE.md §2.5).
"""

import pytest
import sympy as sp

from sciengine.exceptions import ExpressionParseError
from sciengine.symbolic.parsing import (
    MAX_EXPRESSION_LENGTH,
    parse_equation,
    parse_expression,
)

x = sp.Symbol("x")


class TestHappyPath:
    def test_simple_polynomial(self):
        assert parse_expression("x**2 - 4") == x**2 - 4

    def test_caret_means_power(self):
        assert parse_expression("x^2") == x**2

    def test_implicit_multiplication(self):
        assert parse_expression("2x + 1") == 2 * x + 1

    def test_whitelisted_functions(self):
        expr = parse_expression("sin(x) + log(x)")
        assert expr == sp.sin(x) + sp.log(x)

    def test_equation_with_rhs(self):
        eq = parse_equation("x^2 = 4")
        assert sp.solve(eq, x) == [-2, 2]

    def test_bare_expression_becomes_eq_zero(self):
        eq = parse_equation("x - 1")
        assert eq.rhs == 0


class TestRejection:
    def test_rejects_dunder(self):
        with pytest.raises(ExpressionParseError, match="__"):
            parse_expression("__import__('os').system('id')")

    def test_rejects_quotes_and_brackets(self):
        for payload in ("open('x')", 'f("x")', "[1,2]", "{1:2}", "x@y", "a\\b"):
            with pytest.raises(ExpressionParseError):
                parse_expression(payload)

    def test_rejects_empty(self):
        with pytest.raises(ExpressionParseError, match="empty"):
            parse_expression("   ")

    def test_rejects_oversized_input(self):
        with pytest.raises(ExpressionParseError, match="longer than"):
            parse_expression("x+" * (MAX_EXPRESSION_LENGTH // 2 + 1) + "x")

    def test_rejects_huge_exponents(self):
        with pytest.raises(ExpressionParseError, match="Exponent"):
            parse_expression("x^99999")

    def test_rejects_double_equals_equation(self):
        with pytest.raises(ExpressionParseError, match="single '='"):
            parse_equation("x = 1 = 2")

    def test_unknown_symbols_rejected_when_restricted(self):
        with pytest.raises(ExpressionParseError, match="Unknown symbol"):
            parse_expression("x + y", allowed_symbols={"x"})

    def test_unlisted_names_stay_inert(self):
        # Names outside the whitelist must never resolve to Python callables.
        # (With implicit multiplication, unknown multi-letter names split into
        # products of single-letter symbols: "foo(x)" -> f*o*o*x — inert.)
        expr = parse_expression("foo(x) + bar")
        assert all(isinstance(atom, sp.Symbol | sp.Number) for atom in expr.atoms())
        assert sp.Symbol("f") in expr.free_symbols


class TestComplexityGuards:
    def test_rejects_exponent_towers_fast(self):
        # 10^10^10 would allocate gigabytes if evaluated; the evaluate=False
        # pre-parse must reject it before any evaluation happens.
        import time

        start = time.monotonic()
        with pytest.raises(ExpressionParseError, match="Exponent"):
            parse_expression("10^10^10")
        assert time.monotonic() - start < 2.0

    def test_rejects_long_integer_literals(self):
        with pytest.raises(ExpressionParseError, match="digits"):
            parse_expression("1" * 31 + " + x")

    def test_rejects_deep_nesting(self):
        nested = "sin(" * 40 + "x" + ")" * 40
        with pytest.raises(ExpressionParseError, match="nests deeper"):
            parse_expression(nested)

    def test_rejects_integer_growth_after_evaluation(self):
        # Each factor passes the exponent cap; the product's bit-length doesn't.
        with pytest.raises(ExpressionParseError, match="bits"):
            parse_expression("(2^9999) * (2^9999)")

    def test_symbolic_exponents_still_allowed(self):
        expr = parse_expression("x^n")
        assert expr == sp.Symbol("x") ** sp.Symbol("n")
