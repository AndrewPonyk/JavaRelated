"""Effect sizes — a p-value without one is half an answer (TECH-NOTES §3.6.13)."""

from __future__ import annotations

import math
from dataclasses import dataclass

import numpy as np


@dataclass(frozen=True)
class EffectSize:
    name: str
    value: float
    magnitude: str  # negligible | small | medium | large | unknown


def _magnitude(value: float, thresholds: tuple[float, float, float]) -> str:
    if math.isnan(value):
        return "unknown"
    small, medium, large = thresholds
    v = abs(value)
    if v < small:
        return "negligible"
    if v < medium:
        return "small"
    if v < large:
        return "medium"
    return "large"


def cohens_d(a: np.ndarray, b: np.ndarray) -> EffectSize:
    """Pooled-SD standardized mean difference (thresholds per Cohen 1988)."""
    x, y = np.asarray(a, dtype=float), np.asarray(b, dtype=float)
    na, nb = x.size, y.size
    if na < 2 or nb < 2:
        return EffectSize("Cohen's d", float("nan"), "unknown")
    pooled_var = ((na - 1) * x.var(ddof=1) + (nb - 1) * y.var(ddof=1)) / (na + nb - 2)
    d = (x.mean() - y.mean()) / math.sqrt(pooled_var) if pooled_var > 0 else float("nan")
    return EffectSize("Cohen's d", float(d), _magnitude(float(d), (0.2, 0.5, 0.8)))


def hedges_g(a: np.ndarray, b: np.ndarray) -> EffectSize:
    """Small-sample-corrected Cohen's d."""
    d = cohens_d(a, b)
    n = np.asarray(a).size + np.asarray(b).size
    correction = 1.0 - 3.0 / (4.0 * n - 9.0) if n > 2 else float("nan")
    g = d.value * correction
    return EffectSize("Hedges' g", float(g), _magnitude(float(g), (0.2, 0.5, 0.8)))


def rank_biserial_from_u(u_statistic: float, n1: int, n2: int) -> EffectSize:
    """Rank-biserial correlation derived from the Mann-Whitney U statistic."""
    r = 1.0 - (2.0 * u_statistic) / (n1 * n2) if n1 and n2 else float("nan")
    return EffectSize("rank-biserial r", float(r), _magnitude(float(r), (0.1, 0.3, 0.5)))


def cliffs_delta(a: np.ndarray, b: np.ndarray) -> EffectSize:
    """Cliff's δ (dominance), computed in O(n log n) via the Mann-Whitney U identity:
    δ = 2·U₁/(n·m) − 1, where U₁ credits ties with ½ (a tied pair contributes 0 to δ)."""
    from scipy.stats import mannwhitneyu

    x, y = np.asarray(a, dtype=float), np.asarray(b, dtype=float)
    if x.size == 0 or y.size == 0:
        return EffectSize("Cliff's δ", float("nan"), "unknown")
    u1 = float(mannwhitneyu(x, y, alternative="two-sided").statistic)
    delta = 2.0 * u1 / (x.size * y.size) - 1.0
    return EffectSize("Cliff's δ", float(delta), _magnitude(float(delta), (0.147, 0.33, 0.474)))


def cramers_v(chi2: float, n: int, n_rows: int, n_cols: int) -> EffectSize:
    """Cramér's V from a chi-square statistic and table shape."""
    k = min(n_rows - 1, n_cols - 1)
    v = math.sqrt(chi2 / (n * k)) if n > 0 and k > 0 else float("nan")
    return EffectSize("Cramér's V", float(v), _magnitude(float(v), (0.1, 0.3, 0.5)))


def eta_squared(groups: list[np.ndarray]) -> EffectSize:
    """η² from the one-way ANOVA decomposition (SS_between / SS_total)."""
    arrays = [np.asarray(g, dtype=float) for g in groups]
    all_values = np.concatenate(arrays)
    grand = all_values.mean()
    ss_between = sum(g.size * (g.mean() - grand) ** 2 for g in arrays)
    ss_total = float(np.sum((all_values - grand) ** 2))
    eta2 = ss_between / ss_total if ss_total > 0 else float("nan")
    return EffectSize("η² (eta squared)", float(eta2), _magnitude(float(eta2), (0.01, 0.06, 0.14)))


def risk_difference(p_a: float, p_b: float) -> EffectSize:
    """Absolute difference in proportions (B − A) — the A/B tester's lift."""
    diff = p_b - p_a
    return EffectSize("risk difference", float(diff), _magnitude(float(diff), (0.01, 0.05, 0.10)))
