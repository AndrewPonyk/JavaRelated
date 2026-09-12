"""Uniform hypothesis-test runners.

Every runner returns ``TestResult`` so the UI and persistence never special-case
individual tests. Precondition failures raise ``AnalysisError`` with analyst-readable
messages — the app never renders NaN (TECH-NOTES §3.6.12).
"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass

import numpy as np
import pandas as pd
from scipy import stats

from app.core.errors import AnalysisError
from app.stats import effect_sizes as es
from app.stats.test_selector import LABELS, StatTest


@dataclass(frozen=True)
class TestResult:
    test: StatTest
    statistic: float
    p_value: float
    alpha: float
    df: float | None = None
    effect_size: es.EffectSize | None = None
    interpretation: str = ""
    warnings: tuple[str, ...] = ()

    @property
    def significant(self) -> bool:
        return self.p_value < self.alpha

    @property
    def label(self) -> str:
        return LABELS[self.test]

    def to_json_dict(self) -> dict[str, object]:
        """Serializable form persisted into analysis_runs.results_json."""
        return {
            "test": self.test.value,
            "statistic": self.statistic,
            "p_value": self.p_value,
            "alpha": self.alpha,
            "df": self.df,
            "effect_size": (
                {
                    "name": self.effect_size.name,
                    "value": self.effect_size.value,
                    "magnitude": self.effect_size.magnitude,
                }
                if self.effect_size is not None
                else None
            ),
            "significant": self.significant,
            "interpretation": self.interpretation,
            "warnings": list(self.warnings),
        }


def coerce_binary(series: pd.Series) -> tuple[np.ndarray, tuple[str, ...]]:
    """Coerce a 2-level series to {0,1}. Returns (values, warnings about the mapping)."""
    non_null = series.dropna()
    values = pd.unique(non_null)
    if len(values) != 2:
        raise AnalysisError(
            f"expected 2 distinct values, got {len(values)}",
            user_message="A binary metric must have exactly two distinct values.",
        )
    try:
        numeric = non_null.astype(float)
        if set(np.unique(numeric)) == {0.0, 1.0}:
            return numeric.to_numpy(), ()
    except (TypeError, ValueError):
        pass
    low, high = sorted(map(str, values))
    mapped = (non_null.astype(str) == high).astype(float).to_numpy()
    return mapped, (f"Mapped '{high}' → 1 and '{low}' → 0 for the proportion test.",)


def _interpret(p: float, alpha: float, outcome: str, group: str) -> str:
    if p < alpha:
        return (
            f"Reject H₀ at α={alpha:g} (p={p:.4g}): evidence that '{outcome}' "
            f"differs across '{group}'."
        )
    return (
        f"Fail to reject H₀ at α={alpha:g} (p={p:.4g}): no evidence that '{outcome}' "
        f"differs across '{group}'."
    )


def _grouped_arrays(
    df: pd.DataFrame, outcome: str, group: str
) -> tuple[list[np.ndarray], list[str]]:
    frame = df[[outcome, group]].dropna()
    levels = sorted(frame[group].unique(), key=str)
    arrays = [frame.loc[frame[group] == lvl, outcome].to_numpy(dtype=float) for lvl in levels]
    return arrays, [str(lvl) for lvl in levels]


def _two_group_arrays(
    df: pd.DataFrame, outcome: str, group: str
) -> tuple[np.ndarray, np.ndarray, list[str]]:
    arrays, levels = _grouped_arrays(df, outcome, group)
    if len(arrays) != 2:
        raise AnalysisError(
            f"expected 2 groups, got {len(arrays)}",
            user_message="This test needs exactly two groups.",
        )
    a, b = arrays
    if np.ptp(a) == 0 and np.ptp(b) == 0:
        raise AnalysisError(
            "zero variance in both groups",
            user_message=f"'{outcome}' has zero variance in both groups — nothing to test.",
        )
    return a, b, levels


def run_test(
    test: StatTest, df: pd.DataFrame, outcome: str, group: str, *, alpha: float = 0.05
) -> TestResult:
    """Dispatch to the runner for ``test``; single entry point used by services."""
    runner = _RUNNERS.get(test)
    if runner is None:
        raise AnalysisError(
            f"no runner for {test.value}",
            user_message=f"{LABELS[test]} is not implemented yet.",
        )
    return runner(df, outcome, group, alpha)


# ── Continuous outcomes ─────────────────────────────────────────────────


def _student_t(df: pd.DataFrame, outcome: str, group: str, alpha: float) -> TestResult:
    a, b, _ = _two_group_arrays(df, outcome, group)
    t, p = stats.ttest_ind(a, b, equal_var=True)
    return TestResult(
        StatTest.STUDENT_T,
        float(t),
        float(p),
        alpha,
        df=float(a.size + b.size - 2),
        effect_size=es.cohens_d(a, b),
        interpretation=_interpret(float(p), alpha, outcome, group),
    )


def _welch_t(df: pd.DataFrame, outcome: str, group: str, alpha: float) -> TestResult:
    a, b, _ = _two_group_arrays(df, outcome, group)
    t, p = stats.ttest_ind(a, b, equal_var=False)
    va, vb = a.var(ddof=1) / a.size, b.var(ddof=1) / b.size
    dof = (va + vb) ** 2 / (va**2 / (a.size - 1) + vb**2 / (b.size - 1))  # Welch–Satterthwaite
    return TestResult(
        StatTest.WELCH_T,
        float(t),
        float(p),
        alpha,
        df=float(dof),
        effect_size=es.cohens_d(a, b),
        interpretation=_interpret(float(p), alpha, outcome, group),
    )


def _paired_t(df: pd.DataFrame, outcome: str, group: str, alpha: float) -> TestResult:
    a, b, _ = _two_group_arrays(df, outcome, group)
    if a.size != b.size:
        raise AnalysisError(
            "paired groups differ in size",
            user_message="Paired tests need the same number of observations in each group.",
        )
    t, p = stats.ttest_rel(a, b)
    return TestResult(
        StatTest.PAIRED_T,
        float(t),
        float(p),
        alpha,
        df=float(a.size - 1),
        effect_size=es.cohens_d(a, b),
        interpretation=_interpret(float(p), alpha, outcome, group),
        warnings=(
            "Rows were paired by position — sort both groups by the same subject key "
            "before uploading so pairs align.",
        ),
    )


def _mann_whitney(df: pd.DataFrame, outcome: str, group: str, alpha: float) -> TestResult:
    a, b, _ = _two_group_arrays(df, outcome, group)
    u, p = stats.mannwhitneyu(a, b, alternative="two-sided")
    return TestResult(
        StatTest.MANN_WHITNEY_U,
        float(u),
        float(p),
        alpha,
        effect_size=es.rank_biserial_from_u(float(u), a.size, b.size),
        interpretation=_interpret(float(p), alpha, outcome, group),
    )


def _wilcoxon(df: pd.DataFrame, outcome: str, group: str, alpha: float) -> TestResult:
    a, b, _ = _two_group_arrays(df, outcome, group)
    if a.size != b.size:
        raise AnalysisError(
            "paired groups differ in size",
            user_message="Paired tests need the same number of observations in each group.",
        )
    w, p = stats.wilcoxon(a, b)
    return TestResult(
        StatTest.WILCOXON_SIGNED_RANK,
        float(w),
        float(p),
        alpha,
        interpretation=_interpret(float(p), alpha, outcome, group),
        warnings=("Rows were paired by position — ensure sort order aligns subjects.",),
    )


def _one_way_anova(df: pd.DataFrame, outcome: str, group: str, alpha: float) -> TestResult:
    arrays, _ = _grouped_arrays(df, outcome, group)
    f, p = stats.f_oneway(*arrays)
    return TestResult(
        StatTest.ONE_WAY_ANOVA,
        float(f),
        float(p),
        alpha,
        df=float(len(arrays) - 1),
        effect_size=es.eta_squared(arrays),
        interpretation=_interpret(float(p), alpha, outcome, group),
    )


def _welch_anova(df: pd.DataFrame, outcome: str, group: str, alpha: float) -> TestResult:
    arrays, _ = _grouped_arrays(df, outcome, group)
    try:
        from statsmodels.stats.oneway import anova_oneway
    except ImportError as exc:  # pragma: no cover
        raise AnalysisError(
            "statsmodels is required for Welch's ANOVA",
            user_message=(
                "Welch's ANOVA needs the statsmodels package — "
                "Kruskal-Wallis is the recommended fallback."
            ),
        ) from exc
    result = anova_oneway(arrays, use_var="unequal", welch_correction=True)
    return TestResult(
        StatTest.WELCH_ANOVA,
        float(result.statistic),
        float(result.pvalue),
        alpha,
        df=float(result.df_denom),
        effect_size=es.eta_squared(arrays),
        interpretation=_interpret(float(result.pvalue), alpha, outcome, group),
    )


def _kruskal(df: pd.DataFrame, outcome: str, group: str, alpha: float) -> TestResult:
    arrays, _ = _grouped_arrays(df, outcome, group)
    h, p = stats.kruskal(*arrays)
    return TestResult(
        StatTest.KRUSKAL_WALLIS,
        float(h),
        float(p),
        alpha,
        df=float(len(arrays) - 1),
        interpretation=_interpret(float(p), alpha, outcome, group),
    )


# ── Categorical / binary outcomes ───────────────────────────────────────


def _chi_square(df: pd.DataFrame, outcome: str, group: str, alpha: float) -> TestResult:
    frame = df[[outcome, group]].dropna()
    table = pd.crosstab(frame[group], frame[outcome])
    chi2, p, dof, expected = stats.chi2_contingency(table)
    warnings: tuple[str, ...] = ()
    if float(expected.min()) < 5.0:
        warnings = (
            "Some expected cell counts are below 5 — the chi-square approximation is weak here.",
        )
    return TestResult(
        StatTest.CHI_SQUARE,
        float(chi2),
        float(p),
        alpha,
        df=float(dof),
        effect_size=es.cramers_v(float(chi2), int(table.to_numpy().sum()), *table.shape),
        interpretation=_interpret(float(p), alpha, outcome, group),
        warnings=warnings,
    )


def _fisher_exact(df: pd.DataFrame, outcome: str, group: str, alpha: float) -> TestResult:
    frame = df[[outcome, group]].dropna()
    table = pd.crosstab(frame[group], frame[outcome])
    if table.shape != (2, 2):
        raise AnalysisError(
            f"Fisher's exact needs a 2x2 table, got {table.shape}",
            user_message="Fisher's exact test needs exactly two groups and a binary outcome.",
        )
    odds_ratio, p = stats.fisher_exact(table)
    return TestResult(
        StatTest.FISHER_EXACT,
        float(odds_ratio),
        float(p),
        alpha,
        interpretation=(
            _interpret(float(p), alpha, outcome, group) + f" Odds ratio: {odds_ratio:.3g}."
        ),
    )


def _two_proportion_z(df: pd.DataFrame, outcome: str, group: str, alpha: float) -> TestResult:
    try:
        from statsmodels.stats.proportion import proportions_ztest
    except ImportError as exc:  # pragma: no cover
        raise AnalysisError(
            "statsmodels is required for the two-proportion z-test",
            user_message=(
                "The two-proportion z-test needs the statsmodels package — "
                "the chi-square test is an equivalent fallback."
            ),
        ) from exc

    frame = df[[outcome, group]].dropna()
    levels = sorted(frame[group].unique(), key=str)
    if len(levels) != 2:
        raise AnalysisError(
            f"expected 2 groups, got {len(levels)}",
            user_message="This test needs exactly two groups.",
        )
    binary, warnings = coerce_binary(frame[outcome])
    frame = frame.assign(_binary=binary)
    successes = np.array(
        [frame.loc[frame[group] == lvl, "_binary"].sum() for lvl in levels], dtype=float
    )
    nobs = np.array([(frame[group] == lvl).sum() for lvl in levels], dtype=float)
    z, p = proportions_ztest(count=successes, nobs=nobs)
    rate_a, rate_b = successes[0] / nobs[0], successes[1] / nobs[1]
    return TestResult(
        StatTest.TWO_PROPORTION_Z,
        float(z),
        float(p),
        alpha,
        effect_size=es.risk_difference(float(rate_a), float(rate_b)),
        interpretation=(
            _interpret(float(p), alpha, outcome, group)
            + f" Rates: {levels[0]}={rate_a:.2%}, {levels[1]}={rate_b:.2%}."
        ),
        warnings=warnings,
    )


_RUNNERS: dict[StatTest, Callable[[pd.DataFrame, str, str, float], TestResult]] = {
    StatTest.STUDENT_T: _student_t,
    StatTest.WELCH_T: _welch_t,
    StatTest.PAIRED_T: _paired_t,
    StatTest.MANN_WHITNEY_U: _mann_whitney,
    StatTest.WILCOXON_SIGNED_RANK: _wilcoxon,
    StatTest.ONE_WAY_ANOVA: _one_way_anova,
    StatTest.WELCH_ANOVA: _welch_anova,
    StatTest.KRUSKAL_WALLIS: _kruskal,
    StatTest.CHI_SQUARE: _chi_square,
    StatTest.FISHER_EXACT: _fisher_exact,
    StatTest.TWO_PROPORTION_Z: _two_proportion_z,
}
