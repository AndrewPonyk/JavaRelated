"""Orchestrates the analysis pipelines: profile → recommend → execute → persist.

Persistence is write-behind everywhere: a storage failure is logged and the
analysis still returns (docs/ARCHITECTURE.md §2.6). Runs are linked to a saved
dataset when the caller passes ``dataset_id``; ad-hoc analyses simply skip
persistence.
"""

from __future__ import annotations

import time
from dataclasses import dataclass
from typing import Any

import pandas as pd

from app.core.config import get_settings
from app.core.errors import AnalysisError
from app.core.logging import get_logger
from app.stats.distributions import FitResult, fit_distributions, select_candidates
from app.stats.hypothesis_tests import TestResult, run_test
from app.stats.posthoc import PairwiseResult, pairwise_comparisons
from app.stats.profiler import GroupComparisonProfile, VarKind, profile_group_comparison
from app.stats.regression import RegressionResult, fit_logistic, fit_ols
from app.stats.test_selector import StatTest, TestRecommendation, recommend_test

logger = get_logger(__name__)

_PARAMETRIC_OMNIBUS = (StatTest.ONE_WAY_ANOVA, StatTest.WELCH_ANOVA)


@dataclass(frozen=True)
class AnalysisBundle:
    """Everything a page needs to render one analysis, in one immutable object."""

    profile: GroupComparisonProfile
    recommendation: TestRecommendation
    result: TestResult
    duration_ms: int
    posthoc: tuple[PairwiseResult, ...] = ()


@dataclass(frozen=True)
class RunSummary:
    """Plain-data view of a persisted run for the Home page panel."""

    kind: str
    dataset_name: str
    headline: str
    created_at: str
    duration_ms: int | None


def run_group_comparison(
    df: pd.DataFrame,
    outcome: str,
    group: str,
    *,
    paired: bool = False,
    alpha: float = 0.05,
    dataset_id: str | None = None,
    outcome_kind_override: VarKind | None = None,
) -> AnalysisBundle:
    """The flagship flow (docs/ARCHITECTURE.md §2.3), with Holm-corrected follow-ups."""
    started = time.perf_counter()

    profile = profile_group_comparison(
        df, outcome, group, paired=paired, alpha=alpha, outcome_kind_override=outcome_kind_override
    )
    recommendation = recommend_test(profile)
    result = run_test(recommendation.test, df, outcome, group, alpha=alpha)

    posthoc: tuple[PairwiseResult, ...] = ()
    if result.significant and profile.n_groups > 2 and not profile.paired:
        posthoc = tuple(
            pairwise_comparisons(
                df,
                outcome,
                group,
                outcome_kind=profile.outcome_kind,
                parametric=recommendation.test in _PARAMETRIC_OMNIBUS,
                alpha=alpha,
            )
        )

    duration_ms = int((time.perf_counter() - started) * 1000)
    logger.info(
        "Analysis %s on '%s ~ %s': p=%.4g (%d ms, %d follow-ups)",
        recommendation.test.value,
        outcome,
        group,
        result.p_value,
        duration_ms,
        len(posthoc),
    )

    bundle = AnalysisBundle(profile, recommendation, result, duration_ms, posthoc)
    results_json = result.to_json_dict()
    results_json["posthoc"] = [p.to_json_dict() for p in posthoc]
    record_run(
        kind="hypothesis",
        params={
            "outcome": outcome,
            "group": group,
            "alpha": alpha,
            "paired": paired,
            "outcome_kind": profile.outcome_kind.value,
        },
        results=results_json,
        dataset_id=dataset_id,
        duration_ms=duration_ms,
    )
    return bundle


def run_regression(
    df: pd.DataFrame,
    outcome: str,
    features: list[str],
    *,
    model_kind: str = "ols",
    dataset_id: str | None = None,
) -> RegressionResult:
    if model_kind not in ("ols", "logistic"):
        raise AnalysisError(
            f"unknown model_kind: {model_kind!r}",
            user_message="Unknown regression model type.",
        )
    started = time.perf_counter()
    result = (
        fit_logistic(df, outcome, features)
        if model_kind == "logistic"
        else fit_ols(df, outcome, features)
    )
    duration_ms = int((time.perf_counter() - started) * 1000)
    logger.info(
        "Regression %s '%s ~ %d features': n=%d (%d ms)",
        result.model_kind,
        outcome,
        len(features),
        result.n_obs,
        duration_ms,
    )
    record_run(
        kind="regression",
        params={"outcome": outcome, "features": features, "model_kind": result.model_kind},
        results=result.to_json_dict(),
        dataset_id=dataset_id,
        duration_ms=duration_ms,
    )
    return result


def run_distribution_fit(
    df: pd.DataFrame,
    column: str,
    *,
    candidate_names: list[str] | None = None,
    dataset_id: str | None = None,
) -> list[FitResult]:
    started = time.perf_counter()
    candidates = select_candidates(candidate_names) if candidate_names is not None else None
    fits = fit_distributions(df[column].to_numpy(dtype=float), candidates)
    duration_ms = int((time.perf_counter() - started) * 1000)
    logger.info(
        "Distribution fit on '%s': %d candidates ranked (%d ms)", column, len(fits), duration_ms
    )
    record_run(
        kind="distribution",
        params={"column": column, "candidates": candidate_names},
        results={
            "fits": [
                {
                    "name": f.name,
                    "params": list(f.params),
                    "aic": f.aic,
                    "ks_statistic": f.ks_statistic,
                    "ks_p_value": f.ks_p_value,
                }
                for f in fits
            ]
        },
        dataset_id=dataset_id,
        duration_ms=duration_ms,
    )
    return fits


def list_recent_runs(limit: int = 10) -> list[RunSummary]:
    """Recent persisted runs for the Home panel. Empty in demo mode or on storage trouble."""
    if get_settings().demo_mode:
        return []
    try:
        from app.data.db import session_scope
        from app.data.repositories import AnalysisRunRepository

        with session_scope() as session:
            runs = AnalysisRunRepository(session).list_recent(limit=limit)
            return [
                RunSummary(
                    kind=run.kind,
                    dataset_name=run.dataset.name if run.dataset else "?",
                    headline=_headline(run.kind, run.results_json),
                    created_at=run.created_at.isoformat(sep=" ", timespec="seconds")
                    if run.created_at
                    else "",
                    duration_ms=run.duration_ms,
                )
                for run in runs
            ]
    except Exception:
        logger.exception("Listing recent runs failed — rendering an empty panel")
        return []


def _headline(kind: str, results: dict[str, Any]) -> str:
    """One-line summary of a persisted run for list views."""
    if kind in ("hypothesis", "ab_test"):
        p = results.get("p_value")
        test = results.get("test", kind)
        return f"{test}: p={p:.4g}" if isinstance(p, int | float) else str(test)
    if kind == "regression":
        r2 = results.get("r_squared")
        pseudo = results.get("pseudo_r_squared")
        if isinstance(r2, int | float):
            return f"OLS: R²={r2:.3f}"
        if isinstance(pseudo, int | float):
            return f"logistic: pseudo-R²={pseudo:.3f}"
        return str(results.get("model_kind", "regression"))
    if kind == "distribution":
        fits = results.get("fits") or []
        if fits:
            best = fits[0]
            return f"best fit: {best.get('name')} (AIC {best.get('aic', 0):,.0f})"
        return "no candidate fitted"
    return kind


def record_run(
    *,
    kind: str,
    params: dict[str, Any],
    results: dict[str, Any],
    dataset_id: str | None,
    duration_ms: int,
    experiment_id: str | None = None,
) -> str | None:
    """Write-behind persistence: failures never kill an analysis (ARCHITECTURE §2.6).

    Public because other services (A/B experiment logging) record runs through the
    same single code path. Returns the run id, or None when skipped/failed.
    """
    if get_settings().demo_mode or dataset_id is None:
        return None
    try:
        from app.data.db import session_scope
        from app.data.models import AnalysisRun
        from app.data.repositories import AnalysisRunRepository

        with session_scope() as session:
            run = AnalysisRunRepository(session).add(
                AnalysisRun(
                    dataset_id=dataset_id,
                    experiment_id=experiment_id,
                    kind=kind,
                    params_json=params,
                    results_json=results,
                    duration_ms=duration_ms,
                )
            )
            run_id = run.id
        return run_id
    except Exception:
        logger.exception("Persisting the %s run failed (analysis already returned)", kind)
        return None
