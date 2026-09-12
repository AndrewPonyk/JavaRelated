"""Post-hoc pairwise comparisons with Holm correction.

Run only after a significant omnibus test on > 2 groups — uncorrected pairwise
follow-ups silently inflate false positives (docs/TECH-NOTES.md §3.6.9).
"""

from __future__ import annotations

from dataclasses import dataclass
from itertools import combinations

import numpy as np
import pandas as pd
from scipy import stats

from app.core.errors import AnalysisError
from app.stats.profiler import VarKind


@dataclass(frozen=True)
class PairwiseResult:
    group_a: str
    group_b: str
    statistic: float
    p_raw: float
    p_adjusted: float
    significant: bool

    def to_json_dict(self) -> dict[str, object]:
        return {
            "group_a": self.group_a,
            "group_b": self.group_b,
            "statistic": self.statistic,
            "p_raw": self.p_raw,
            "p_adjusted": self.p_adjusted,
            "significant": self.significant,
        }


def holm_adjust(p_values: list[float]) -> list[float]:
    """Holm step-down adjustment (monotone, clipped at 1). Order-preserving."""
    m = len(p_values)
    if m == 0:
        return []
    order = np.argsort(p_values)
    adjusted = np.empty(m, dtype=float)
    running_max = 0.0
    for rank, idx in enumerate(order):
        candidate = (m - rank) * p_values[idx]
        running_max = max(running_max, candidate)
        adjusted[idx] = min(1.0, running_max)
    return adjusted.tolist()


def pairwise_comparisons(
    df: pd.DataFrame,
    outcome: str,
    group: str,
    *,
    outcome_kind: VarKind,
    parametric: bool,
    alpha: float = 0.05,
) -> list[PairwiseResult]:
    """All-pairs follow-up tests with Holm-adjusted p-values.

    - continuous/ordinal outcomes: Welch's t (parametric) or Mann-Whitney U
    - binary/categorical outcomes: chi-square on each 2×k sub-table
    """
    frame = df[[outcome, group]].dropna()
    levels = sorted(map(str, frame[group].astype(str).unique()))
    if len(levels) < 3:
        raise AnalysisError(
            "post-hoc requires > 2 groups",
            user_message="Pairwise follow-ups only apply to comparisons of 3+ groups.",
        )

    pairs = list(combinations(levels, 2))
    statistics: list[float] = []
    raw_p: list[float] = []
    grouped = frame.assign(_g=frame[group].astype(str))

    for level_a, level_b in pairs:
        if outcome_kind in (VarKind.BINARY, VarKind.CATEGORICAL):
            sub = grouped[grouped["_g"].isin([level_a, level_b])]
            table = pd.crosstab(sub["_g"], sub[outcome])
            statistic, p, _, _ = stats.chi2_contingency(table)
        else:
            a = grouped.loc[grouped["_g"] == level_a, outcome].to_numpy(dtype=float)
            b = grouped.loc[grouped["_g"] == level_b, outcome].to_numpy(dtype=float)
            if parametric:
                statistic, p = stats.ttest_ind(a, b, equal_var=False)  # Welch per pair
            else:
                statistic, p = stats.mannwhitneyu(a, b, alternative="two-sided")
        statistics.append(float(statistic))
        raw_p.append(float(p))

    adjusted = holm_adjust(raw_p)
    return [
        PairwiseResult(
            group_a=pair[0],
            group_b=pair[1],
            statistic=statistics[i],
            p_raw=raw_p[i],
            p_adjusted=adjusted[i],
            significant=adjusted[i] < alpha,
        )
        for i, pair in enumerate(pairs)
    ]
