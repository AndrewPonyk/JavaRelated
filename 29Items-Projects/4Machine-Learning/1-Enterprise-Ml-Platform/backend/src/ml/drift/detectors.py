"""Statistical drift detectors (PSI / KS / KL).

Pure functions over distributions so they're trivially unit-testable and can be
reused both online (serving) and offline (batch drift jobs).
"""
from __future__ import annotations

from collections.abc import Sequence

import numpy as np
from scipy.stats import ks_2samp

_EPS = 1e-6  # Laplace smoothing to avoid log(0) / division-by-zero.


def population_stability_index(
    baseline: Sequence[float], current: Sequence[float], bins: int = 10
) -> float:
    """Population Stability Index between two numeric distributions.

    PSI < 0.1 -> no significant shift; 0.1-0.2 -> moderate; > 0.2 -> significant.
    Bins are derived from baseline quantiles so each baseline bin is comparably
    populated; both samples are then bucketed on those edges.
    """
    base = np.asarray(baseline, dtype=float)
    curr = np.asarray(current, dtype=float)
    if base.size == 0 or curr.size == 0:
        return 0.0

    # Quantile-based edges; collapse to a single bin for (near-)constant data.
    quantiles = np.linspace(0, 1, bins + 1)
    edges = np.unique(np.quantile(base, quantiles))
    if edges.size < 2:
        return 0.0
    edges[0], edges[-1] = -np.inf, np.inf

    base_pct = _binned_fractions(base, edges)
    curr_pct = _binned_fractions(curr, edges)
    return float(np.sum((curr_pct - base_pct) * np.log(curr_pct / base_pct)))


def _binned_fractions(values: np.ndarray, edges: np.ndarray) -> np.ndarray:
    counts, _ = np.histogram(values, bins=edges)
    fractions = counts / max(values.size, 1)
    return fractions + _EPS


def ks_statistic(baseline: Sequence[float], current: Sequence[float]) -> float:
    """Two-sample Kolmogorov-Smirnov statistic (0 = identical, 1 = disjoint)."""
    base = np.asarray(baseline, dtype=float)
    curr = np.asarray(current, dtype=float)
    if base.size == 0 or curr.size == 0:
        return 0.0
    return float(ks_2samp(base, curr).statistic)


def kl_divergence(p: Sequence[float], q: Sequence[float]) -> float:
    """KL divergence D(P || Q) for two discrete probability vectors."""
    p_arr = np.asarray(p, dtype=float)
    q_arr = np.asarray(q, dtype=float)
    if p_arr.size == 0 or p_arr.size != q_arr.size:
        return 0.0
    p_norm = p_arr / max(p_arr.sum(), _EPS) + _EPS
    q_norm = q_arr / max(q_arr.sum(), _EPS) + _EPS
    return float(np.sum(p_norm * np.log(p_norm / q_norm)))
