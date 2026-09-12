"""Safe parsing of user-supplied mathematical expressions.

SECURITY — read before touching this file:

``sympy.sympify()`` passes input through ``eval()``. It must NEVER be called
on raw user input anywhere in the platform. This module is the ONLY sanctioned
entry point for turning untrusted text into SymPy objects. Defenses, in order:

1. length cap,
2. dunder (``__``) rejection,
3. character whitelist (no quotes, brackets, ``@``, ``\\`` …),
4. digit-run cap (huge integer literals rejected before tokenizing),
5. a *pre-parse with ``evaluate=False``* whose tree is complexity-guarded
   (operation count, exponent magnitude, integer bit-length, nesting depth)
   BEFORE any evaluation happens — this is what stops ``10^10^10`` from
   allocating gigabytes during evaluation,
6. the real parse with an explicit function whitelist and an EMPTY global
   namespace.

Tests in ``tests/test_parsing.py`` are the regression suite for this attack
surface — extend them with every new bypass idea.
"""

from __future__ import annotations

import re

import sympy as sp
from sympy.parsing.sympy_parser import (
    convert_xor,
    implicit_multiplication_application,
    parse_expr,
    standard_transformations,
)

from sciengine.exceptions import ExpressionParseError

MAX_EXPRESSION_LENGTH = 512
MAX_OPERATIONS = 256
MAX_EXPONENT = 10_000
MAX_INTEGER_DIGITS = 30
MAX_INTEGER_BITS = 4096
MAX_TREE_DEPTH = 32

# Whitelist of characters a math expression may contain. Everything else is
# rejected before the string reaches the parser. Deliberately excluded:
# quotes (string literals), brackets/braces, backslash, '@', '#', '$', '%'.
_ALLOWED_CHARS = re.compile(r"^[0-9a-zA-Z+\-*/^() =,.<>!_]+$")

_LONG_DIGIT_RUN = re.compile(rf"\d{{{MAX_INTEGER_DIGITS + 1},}}")

# Names the parser may resolve to callables/constants. Anything not listed
# becomes an inert Symbol/undefined Function — users cannot reach Python
# builtins or arbitrary SymPy internals.
_SAFE_NAMESPACE: dict[str, object] = {
    # constants
    "pi": sp.pi,
    "E": sp.E,
    "I": sp.I,
    "oo": sp.oo,
    # elementary functions
    "sin": sp.sin,
    "cos": sp.cos,
    "tan": sp.tan,
    "asin": sp.asin,
    "acos": sp.acos,
    "atan": sp.atan,
    "atan2": sp.atan2,
    "sinh": sp.sinh,
    "cosh": sp.cosh,
    "tanh": sp.tanh,
    "exp": sp.exp,
    "log": sp.log,
    "ln": sp.log,
    "sqrt": sp.sqrt,
    "Abs": sp.Abs,
    "abs": sp.Abs,
    "sign": sp.sign,
    "floor": sp.floor,
    "ceiling": sp.ceiling,
    "factorial": sp.factorial,
    "gamma": sp.gamma,
    "Min": sp.Min,
    "Max": sp.Max,
    # required by the tokenizer transformations (auto_number/auto_symbol emit
    # calls to these); removing them breaks parsing of plain numbers.
    "Integer": sp.Integer,
    "Float": sp.Float,
    "Rational": sp.Rational,
    "Symbol": sp.Symbol,
    "Function": sp.Function,
    # required by the evaluate=False pre-parse (the transformer rewrites
    # operators into explicit constructor calls) and by relational input.
    # These are inert tree constructors — they build nodes, they never execute.
    "Add": sp.Add,
    "Mul": sp.Mul,
    "Pow": sp.Pow,
    "Lt": sp.Lt,
    "Le": sp.Le,
    "Gt": sp.Gt,
    "Ge": sp.Ge,
}

_TRANSFORMATIONS = (
    *standard_transformations,
    implicit_multiplication_application,  # "2x"  -> 2*x
    convert_xor,  # "x^2" -> x**2 (classroom convention)
)


def parse_expression(
    text: str,
    *,
    allowed_symbols: set[str] | None = None,
) -> sp.Expr:
    """Parse untrusted ``text`` into a SymPy expression or raise ``ExpressionParseError``.

    ``allowed_symbols`` optionally restricts which free symbols may appear
    (e.g. ``{"x"}`` for single-variable exercises).
    """
    _guard_text(text)

    # Pass 1: parse WITHOUT evaluation and guard the raw tree. Nothing has
    # been computed yet, so exponent bombs are still cheap syntax here.
    unevaluated = _parse(text, evaluate=False)
    _guard_complexity(unevaluated)

    # Pass 2: the guarded expression is safe to evaluate into canonical form.
    expr = _parse(text, evaluate=True)
    _guard_complexity(expr)  # evaluation can grow integers (e.g. 2^1000 * 2^1000)

    if allowed_symbols is not None:
        extra = {s.name for s in expr.free_symbols} - allowed_symbols
        if extra:
            raise ExpressionParseError(
                f"Unknown symbol(s): {', '.join(sorted(extra))}. "
                f"Allowed: {', '.join(sorted(allowed_symbols))}."
            )
    return expr


def parse_equation(text: str, **kwargs) -> sp.Eq:
    """Parse ``lhs = rhs`` (or a bare expression, treated as ``… = 0``) into ``sympy.Eq``."""
    lhs_text, sep, rhs_text = text.partition("=")
    if sep and "=" in rhs_text:
        raise ExpressionParseError("Only a single '=' is allowed in an equation.")
    lhs = parse_expression(lhs_text, **kwargs)
    rhs = parse_expression(rhs_text, **kwargs) if sep else sp.Integer(0)
    return sp.Eq(lhs, rhs, evaluate=False)


def _parse(text: str, *, evaluate: bool) -> sp.Basic:
    try:
        return parse_expr(
            text,
            local_dict=dict(_SAFE_NAMESPACE),
            global_dict={},  # hide Python builtins and SymPy's default namespace
            transformations=_TRANSFORMATIONS,
            evaluate=evaluate,
        )
    except ExpressionParseError:
        raise
    except Exception as exc:  # SymPy raises many exception types here
        raise ExpressionParseError(f"Could not parse expression: {exc}") from exc


def _guard_text(text: str) -> None:
    if not text or not text.strip():
        raise ExpressionParseError("Expression is empty.")
    if len(text) > MAX_EXPRESSION_LENGTH:
        raise ExpressionParseError(f"Expression is longer than {MAX_EXPRESSION_LENGTH} characters.")
    if "__" in text:
        raise ExpressionParseError("Expression contains the forbidden token '__'.")
    if not _ALLOWED_CHARS.match(text):
        raise ExpressionParseError(
            "Expression contains unsupported characters. "
            "Allowed: letters, digits, + - * / ^ ( ) = , . ! < > _"
        )
    if _LONG_DIGIT_RUN.search(text):
        raise ExpressionParseError(
            f"Numbers longer than {MAX_INTEGER_DIGITS} digits are not supported."
        )


def _guard_complexity(expr: sp.Basic) -> None:
    """Reject expressions that would be pathologically expensive to evaluate.

    Runs on the *unevaluated* tree first (so nothing expensive has happened
    yet) and again after evaluation (results can grow). The worker sandbox's
    kill-on-timeout is the second line of defense (sciengine.runtime).
    """
    if sp.count_ops(expr) > MAX_OPERATIONS:
        raise ExpressionParseError(
            f"Expression is too complex (more than {MAX_OPERATIONS} operations)."
        )
    if _tree_depth(expr) > MAX_TREE_DEPTH:
        raise ExpressionParseError(f"Expression nests deeper than {MAX_TREE_DEPTH} levels.")
    for node in sp.preorder_traversal(expr):
        if isinstance(node, sp.Pow):
            _guard_exponent(node.exp)
        if isinstance(node, sp.Integer) and int(node).bit_length() > MAX_INTEGER_BITS:
            raise ExpressionParseError(
                f"Integer values above {MAX_INTEGER_BITS} bits are not supported."
            )


def _guard_exponent(exponent: sp.Basic) -> None:
    if not getattr(exponent, "is_number", False):
        return  # symbolic exponents (x^n) are fine — nothing to evaluate yet
    try:
        magnitude = abs(float(exponent.evalf()))  # evalf: float math, no bignums
    except (TypeError, ValueError, OverflowError) as exc:
        raise ExpressionParseError("Exponent could not be bounded; rejected.") from exc
    if magnitude > MAX_EXPONENT:
        raise ExpressionParseError(f"Exponent magnitude exceeds the limit of {MAX_EXPONENT}.")


def _tree_depth(expr: sp.Basic) -> int:
    depth = 1
    stack: list[tuple[sp.Basic, int]] = [(expr, 1)]
    while stack:
        node, d = stack.pop()
        depth = max(depth, d)
        stack.extend((arg, d + 1) for arg in node.args)
    return depth
