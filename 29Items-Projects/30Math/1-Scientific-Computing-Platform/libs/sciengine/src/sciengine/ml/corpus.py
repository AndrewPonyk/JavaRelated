"""Synthetic labeled corpus generation for the equation-pattern recognizer.

Labels are ground truth *by construction* (each generator emits expressions of
its own class), which bootstraps training without hand labeling. Expressions
are rendered as classroom-style text and round-trip through the safe parser.
"""

from __future__ import annotations

import random
from collections.abc import Callable

import sympy as sp

from sciengine.ml.features import PatternLabel

x = sp.Symbol("x")


def _nonzero(rng: random.Random, lo: int = -9, hi: int = 9) -> int:
    value = 0
    while value == 0:
        value = rng.randint(lo, hi)
    return value


def _linear(rng: random.Random) -> sp.Expr:
    return _nonzero(rng) * x + rng.randint(-9, 9)


def _quadratic(rng: random.Random) -> sp.Expr:
    return _nonzero(rng) * x**2 + rng.randint(-9, 9) * x + rng.randint(-9, 9)


def _polynomial(rng: random.Random) -> sp.Expr:
    degree = rng.randint(3, 6)
    expr = _nonzero(rng) * x**degree
    for power in range(degree):
        if rng.random() < 0.6:
            expr += rng.randint(-9, 9) * x**power
    return expr


def _trigonometric(rng: random.Random) -> sp.Expr:
    fn = rng.choice([sp.sin, sp.cos, sp.tan])
    expr = _nonzero(rng) * fn(_nonzero(rng, -4, 4) * x)
    if rng.random() < 0.5:
        expr += rng.choice([sp.sin, sp.cos])(x)
    return expr + rng.randint(-3, 3)


def _exponential(rng: random.Random) -> sp.Expr:
    return _nonzero(rng) * sp.exp(_nonzero(rng, -4, 4) * x) + rng.randint(-5, 5)


def _logarithmic(rng: random.Random) -> sp.Expr:
    return _nonzero(rng) * sp.log(_nonzero(rng, 1, 6) * x) + rng.randint(-5, 5)


def _rational(rng: random.Random) -> sp.Expr:
    numerator = _nonzero(rng) * x + rng.randint(-9, 9)
    denominator = _nonzero(rng) * x + _nonzero(rng)
    return numerator / denominator


_GENERATORS: dict[PatternLabel, Callable[[random.Random], sp.Expr]] = {
    PatternLabel.LINEAR: _linear,
    PatternLabel.QUADRATIC: _quadratic,
    PatternLabel.POLYNOMIAL: _polynomial,
    PatternLabel.TRIGONOMETRIC: _trigonometric,
    PatternLabel.EXPONENTIAL: _exponential,
    PatternLabel.LOGARITHMIC: _logarithmic,
    PatternLabel.RATIONAL: _rational,
}


def generate_expression(label: PatternLabel, rng: random.Random) -> str:
    """One expression of the given class, as parser-compatible text."""
    generator = _GENERATORS.get(label)
    if generator is None:
        raise ValueError(f"No generator for label {label!r}")
    return sp.sstr(generator(rng))


def generate_corpus(n_per_label: int = 200, *, seed: int = 0) -> tuple[list[str], list[str]]:
    """Balanced labeled corpus: (expressions, labels). Deterministic per seed."""
    rng = random.Random(seed)
    expressions: list[str] = []
    labels: list[str] = []
    for label in _GENERATORS:
        for _ in range(n_per_label):
            expressions.append(generate_expression(label, rng))
            labels.append(str(label))
    return expressions, labels
