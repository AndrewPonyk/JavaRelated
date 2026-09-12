"""Expression-tree featurization + heuristic pattern classification.

The heuristic classifier is the permanent *fallback* for the SageMaker
endpoint (docs/ARCHITECTURE.md §2.2) and the labeling bootstrap for training
data generation.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum
from typing import Literal

import sympy as sp

from sciengine.symbolic.parsing import parse_expression


class PatternLabel(StrEnum):
    LINEAR = "linear"
    QUADRATIC = "quadratic"
    POLYNOMIAL = "polynomial"
    TRIGONOMETRIC = "trigonometric"
    EXPONENTIAL = "exponential"
    LOGARITHMIC = "logarithmic"
    RATIONAL = "rational"
    UNKNOWN = "unknown"


@dataclass(frozen=True)
class PatternPrediction:
    label: PatternLabel
    confidence: float
    source: Literal["heuristic", "sagemaker"]


_TRIG = (sp.sin, sp.cos, sp.tan, sp.asin, sp.acos, sp.atan)


def expression_features(expression_text: str) -> dict[str, float]:
    """Deterministic numeric features of an expression tree.

    Used verbatim by ml/training/train.py (via this import) and by the local
    heuristic. Keep the key set append-only: models are trained against it.
    """
    expr = parse_expression(expression_text)
    nodes = list(sp.preorder_traversal(expr))
    funcs = [n for n in nodes if isinstance(n, sp.Function)]
    return {
        "op_count": float(sp.count_ops(expr)),
        "node_count": float(len(nodes)),
        "depth": float(_tree_depth(expr)),
        "n_symbols": float(len(expr.free_symbols)),
        "n_trig": float(sum(isinstance(f, _TRIG) for f in funcs)),
        "n_exp": float(sum(isinstance(f, sp.exp) for f in funcs)),
        "n_log": float(sum(isinstance(f, sp.log) for f in funcs)),
        "is_polynomial": float(_polynomial_degree(expr) is not None),
        "poly_degree": float(_polynomial_degree(expr) or -1),
    }


def classify_pattern(expression_text: str) -> PatternPrediction:
    """Rule-based classification — the SageMaker fallback path."""
    expr = parse_expression(expression_text)
    has = {f.func for f in expr.atoms(sp.Function)}

    if has & set(_TRIG):
        return PatternPrediction(PatternLabel.TRIGONOMETRIC, 0.7, "heuristic")
    if sp.exp in has:
        return PatternPrediction(PatternLabel.EXPONENTIAL, 0.7, "heuristic")
    if sp.log in has:
        return PatternPrediction(PatternLabel.LOGARITHMIC, 0.7, "heuristic")

    degree = _polynomial_degree(expr)
    if degree == 1:
        return PatternPrediction(PatternLabel.LINEAR, 0.9, "heuristic")
    if degree == 2:
        return PatternPrediction(PatternLabel.QUADRATIC, 0.9, "heuristic")
    if degree is not None:
        return PatternPrediction(PatternLabel.POLYNOMIAL, 0.8, "heuristic")
    if expr.free_symbols and expr.is_rational_function(*expr.free_symbols):
        return PatternPrediction(PatternLabel.RATIONAL, 0.6, "heuristic")
    return PatternPrediction(PatternLabel.UNKNOWN, 0.3, "heuristic")


def _tree_depth(expr: sp.Basic) -> int:
    if not expr.args:
        return 1
    return 1 + max(_tree_depth(arg) for arg in expr.args)


def _polynomial_degree(expr: sp.Basic) -> int | None:
    symbols = list(expr.free_symbols)
    if not symbols:
        return 0
    try:
        poly = sp.Poly(expr, *symbols)
    except sp.PolynomialError:
        return None
    return int(poly.total_degree())
