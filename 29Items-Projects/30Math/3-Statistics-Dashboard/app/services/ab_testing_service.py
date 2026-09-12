"""A/B testing: frequentist + Bayesian conversion analysis, SRM guardrail,
sample-size planning, and experiment logging.

The SRM check runs before any verdict; a broken split invalidates the experiment
regardless of the p-value (docs/TECH-NOTES.md §3.6.11). When an experiment has a
pre-registered ``planned_n_per_variant``, analyses below that n are flagged as
interim looks (§3.6.10 — peeking manufactures false positives).
"""

from __future__ import annotations

import math
import time
from dataclasses import dataclass

import numpy as np
import pandas as pd
from scipy import stats

from app.core.config import get_settings
from app.core.errors import AnalysisError, DataValidationError
from app.core.logging import get_logger
from app.stats.hypothesis_tests import coerce_binary

logger = get_logger(__name__)

#: Conventional strict alpha for sample-ratio-mismatch detection.
SRM_ALPHA = 0.001

#: Monte Carlo draws for the Bayesian posterior (seeded — deterministic output).
_POSTERIOR_SAMPLES = 100_000
_POSTERIOR_SEED = 42


@dataclass(frozen=True)
class VariantStats:
    name: str
    n: int
    conversions: int

    @property
    def rate(self) -> float:
        return self.conversions / self.n if self.n else float("nan")


@dataclass(frozen=True)
class SrmCheck:
    passed: bool
    p_value: float
    observed: dict[str, int]
    expected_ratio: float


@dataclass(frozen=True)
class BayesianConversion:
    """Beta-Binomial posterior comparison (uniform Beta(1,1) prior)."""

    prob_b_beats_a: float
    expected_lift: float  # posterior mean of rate_b - rate_a
    lift_ci_low: float  # 95% credible interval on the lift
    lift_ci_high: float
    a_alpha: float
    a_beta: float
    b_alpha: float
    b_beta: float


@dataclass(frozen=True)
class ConversionAnalysis:
    variant_a: VariantStats
    variant_b: VariantStats
    absolute_lift: float  # rate_b - rate_a
    relative_lift: float  # (rate_b - rate_a) / rate_a
    lift_ci_low: float  # 95% Newcombe CI on the absolute lift
    lift_ci_high: float
    z_statistic: float
    p_value: float
    alpha: float
    srm: SrmCheck
    bayesian: BayesianConversion
    warnings: tuple[str, ...] = ()

    @property
    def significant(self) -> bool:
        return self.p_value < self.alpha

    def to_json_dict(self) -> dict[str, object]:
        return {
            "test": "two_proportion_z",
            "variant_a": {
                "name": self.variant_a.name,
                "n": self.variant_a.n,
                "conversions": self.variant_a.conversions,
            },
            "variant_b": {
                "name": self.variant_b.name,
                "n": self.variant_b.n,
                "conversions": self.variant_b.conversions,
            },
            "absolute_lift": self.absolute_lift,
            "relative_lift": self.relative_lift,
            "lift_ci": [self.lift_ci_low, self.lift_ci_high],
            "z_statistic": self.z_statistic,
            "p_value": self.p_value,
            "alpha": self.alpha,
            "significant": self.significant,
            "srm": {"passed": self.srm.passed, "p_value": self.srm.p_value},
            "prob_b_beats_a": self.bayesian.prob_b_beats_a,
            "warnings": list(self.warnings),
        }


def srm_check(observed: dict[str, int], expected_ratio: float = 0.5) -> SrmCheck:
    """Sample-ratio mismatch: chi-square of the observed split against the design."""
    counts = list(observed.values())
    if len(counts) != 2:
        raise AnalysisError("SRM check expects exactly 2 variants")
    total = sum(counts)
    expected = [total * expected_ratio, total * (1.0 - expected_ratio)]
    _, p_value = stats.chisquare(counts, f_exp=expected)
    return SrmCheck(
        passed=bool(p_value >= SRM_ALPHA),
        p_value=float(p_value),
        observed=dict(observed),
        expected_ratio=expected_ratio,
    )


def bayesian_conversion(
    a: VariantStats,
    b: VariantStats,
    *,
    prior_alpha: float = 1.0,
    prior_beta: float = 1.0,
) -> BayesianConversion:
    """Beta-Binomial posterior comparison via seeded Monte Carlo (deterministic)."""
    a_alpha, a_beta = prior_alpha + a.conversions, prior_beta + a.n - a.conversions
    b_alpha, b_beta = prior_alpha + b.conversions, prior_beta + b.n - b.conversions

    rng = np.random.default_rng(_POSTERIOR_SEED)
    samples_a = rng.beta(a_alpha, a_beta, _POSTERIOR_SAMPLES)
    samples_b = rng.beta(b_alpha, b_beta, _POSTERIOR_SAMPLES)
    lift = samples_b - samples_a

    low, high = np.percentile(lift, [2.5, 97.5])
    return BayesianConversion(
        prob_b_beats_a=float(np.mean(samples_b > samples_a)),
        expected_lift=float(lift.mean()),
        lift_ci_low=float(low),
        lift_ci_high=float(high),
        a_alpha=float(a_alpha),
        a_beta=float(a_beta),
        b_alpha=float(b_alpha),
        b_beta=float(b_beta),
    )


def analyze_conversion(
    df: pd.DataFrame,
    variant_col: str,
    outcome_col: str,
    *,
    alpha: float = 0.05,
    expected_ratio: float = 0.5,
    planned_n_per_variant: int | None = None,
) -> ConversionAnalysis:
    """Two-variant conversion analysis: frequentist verdict + Bayesian companion."""
    if variant_col == outcome_col:
        raise AnalysisError(
            "variant equals metric",
            user_message="The variant column and the conversion metric must be different columns.",
        )
    try:
        from statsmodels.stats.proportion import confint_proportions_2indep, proportions_ztest
    except ImportError as exc:  # pragma: no cover
        raise AnalysisError(
            "statsmodels is not installed",
            user_message="A/B analysis requires the statsmodels package.",
        ) from exc

    frame = df[[variant_col, outcome_col]].dropna()
    levels = sorted(frame[variant_col].astype(str).unique())
    if len(levels) != 2:
        raise AnalysisError(
            f"expected 2 variants, got {len(levels)}",
            user_message=f"'{variant_col}' must contain exactly two variants.",
        )

    binary, warnings = coerce_binary(frame[outcome_col])
    frame = frame.assign(_binary=binary, _variant=frame[variant_col].astype(str))

    variants = []
    for level in levels:
        mask = frame["_variant"] == level
        variants.append(
            VariantStats(
                name=level,
                n=int(mask.sum()),
                conversions=int(frame.loc[mask, "_binary"].sum()),
            )
        )
    a, b = variants

    srm = srm_check({a.name: a.n, b.name: b.n}, expected_ratio)
    extra: list[str] = list(warnings)
    if not srm.passed:
        extra.append(
            f"Sample-ratio mismatch detected (p={srm.p_value:.2g}): the observed split "
            f"{a.n}/{b.n} deviates from the expected {expected_ratio:.0%} — "
            "treat these results as unreliable and investigate the assignment mechanism."
        )
    if min(a.conversions, b.conversions) < 5:
        extra.append("Fewer than 5 conversions in a variant — the normal approximation is weak.")
    if planned_n_per_variant is not None and min(a.n, b.n) < planned_n_per_variant:
        extra.append(
            f"Interim look: the smallest variant has n={min(a.n, b.n):,} of the planned "
            f"{planned_n_per_variant:,} — an early p < α here is not a valid stopping "
            "signal (peeking inflates false positives)."
        )

    z, p = proportions_ztest(
        count=np.array([b.conversions, a.conversions], dtype=float),
        nobs=np.array([b.n, a.n], dtype=float),
    )
    ci_low, ci_high = confint_proportions_2indep(
        b.conversions, b.n, a.conversions, a.n, compare="diff", alpha=0.05
    )
    absolute = b.rate - a.rate
    relative = absolute / a.rate if a.rate > 0 else float("nan")

    logger.info(
        "A/B conversion: %s=%.4f vs %s=%.4f, p=%.4g", a.name, a.rate, b.name, b.rate, float(p)
    )
    return ConversionAnalysis(
        variant_a=a,
        variant_b=b,
        absolute_lift=float(absolute),
        relative_lift=float(relative),
        lift_ci_low=float(ci_low),
        lift_ci_high=float(ci_high),
        z_statistic=float(z),
        p_value=float(p),
        alpha=alpha,
        srm=srm,
        bayesian=bayesian_conversion(a, b),
        warnings=tuple(extra),
    )


def required_sample_size(
    baseline_rate: float,
    mde_relative: float,
    *,
    alpha: float = 0.05,
    power: float = 0.8,
) -> int:
    """Per-variant n to detect a relative MDE over the baseline conversion rate."""
    try:
        from statsmodels.stats.power import NormalIndPower
        from statsmodels.stats.proportion import proportion_effectsize
    except ImportError as exc:  # pragma: no cover
        raise AnalysisError(
            "statsmodels is not installed",
            user_message="The sample-size planner requires the statsmodels package.",
        ) from exc

    if not 0.0 < baseline_rate < 1.0:
        raise AnalysisError(
            "baseline out of range", user_message="Baseline rate must be between 0 and 100%."
        )
    target_rate = baseline_rate * (1.0 + mde_relative)
    if mde_relative <= 0 or target_rate >= 1.0:
        raise AnalysisError(
            "MDE out of range",
            user_message="The minimum detectable effect must be positive and keep the "
            "target rate below 100%.",
        )

    effect = proportion_effectsize(target_rate, baseline_rate)
    n = NormalIndPower().solve_power(
        effect_size=effect, alpha=alpha, power=power, alternative="two-sided"
    )
    return int(math.ceil(float(n)))


def log_experiment_run(
    df: pd.DataFrame,
    dataset_name: str,
    analysis: ConversionAnalysis,
    experiment_id: str,
    *,
    variant_col: str,
    outcome_col: str,
) -> str:
    """Persist an A/B analysis against an experiment.

    The active data is saved to the library first (deduplicated by content hash)
    so every logged result references its exact data snapshot.
    """
    if get_settings().demo_mode:
        raise DataValidationError(
            "experiment logging needs a database",
            user_message="Configure DATABASE_URL to log results to experiments.",
        )
    from app.services import dataset_service
    from app.services.analysis_service import record_run

    started = time.perf_counter()
    dataset_id = dataset_service.save_dataset(df, dataset_name, source_type="upload")
    run_id = record_run(
        kind="ab_test",
        params={
            "variant_col": variant_col,
            "outcome_col": outcome_col,
            "alpha": analysis.alpha,
            "expected_ratio": analysis.srm.expected_ratio,
        },
        results=analysis.to_json_dict(),
        dataset_id=dataset_id,
        duration_ms=int((time.perf_counter() - started) * 1000),
        experiment_id=experiment_id,
    )
    if run_id is None:
        raise AnalysisError(
            "experiment run persistence failed",
            user_message="The result could not be logged — check the storage logs.",
        )
    logger.info("A/B run %s logged to experiment %s", run_id, experiment_id)
    return run_id
