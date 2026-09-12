"""LaTeX rendering of expressions and step-by-step derivations.

Output from here is rendered client-side by KaTeX with ``trust: false``.
Never feed user-controlled LaTeX to a TeX binary — see docs/TECH-NOTES.md §3.6.
"""

from __future__ import annotations

import sympy as sp

from sciengine.symbolic.parsing import parse_equation, parse_expression


def expression_to_latex(expression_text: str) -> str:
    """Canonical LaTeX for a user-typed expression (used for live preview)."""
    return sp.latex(parse_expression(expression_text))


def equation_to_latex(equation_text: str) -> str:
    """Canonical LaTeX for ``lhs = rhs`` input."""
    return sp.latex(parse_equation(equation_text))


def derivation_to_latex(steps: list[str]) -> str:
    """Render ordered derivation steps as a single aligned LaTeX block.

    Steps come from :mod:`sciengine.symbolic.steps` (already LaTeX lines);
    KaTeX renders the ``aligned`` environment in display mode.
    """
    if not steps:
        return ""
    body = " \\\\\n".join(f"& {step}" for step in steps)
    return f"\\begin{{aligned}}\n{body}\n\\end{{aligned}}"
