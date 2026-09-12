"""A/B service: SRM guardrail, frequentist + Bayesian analysis, planner, experiment logging."""

from __future__ import annotations

import pandas as pd
import pytest

from app.core.config import Settings
from app.core.errors import AnalysisError
from app.services import ab_testing_service as ab


def test_srm_passes_on_balanced_split() -> None:
    check = ab.srm_check({"A": 5000, "B": 5015})
    assert check.passed


def test_srm_fails_on_broken_split() -> None:
    check = ab.srm_check({"A": 5500, "B": 4500})
    assert not check.passed
    assert check.p_value < ab.SRM_ALPHA


def test_analyze_conversion_detects_lift(ab_df: pd.DataFrame) -> None:
    pytest.importorskip("statsmodels")
    analysis = ab.analyze_conversion(ab_df, "variant", "converted")
    assert analysis.variant_a.name == "A"
    assert analysis.variant_b.rate > analysis.variant_a.rate
    assert analysis.absolute_lift > 0
    assert analysis.significant  # 10% vs 18% at n=400/group
    assert analysis.srm.passed  # 400/400 split
    # The 95% CI must bracket the observed lift and exclude zero here.
    assert analysis.lift_ci_low < analysis.absolute_lift < analysis.lift_ci_high
    assert analysis.lift_ci_low > 0


def test_bayesian_view_agrees_with_frequentist(ab_df: pd.DataFrame) -> None:
    pytest.importorskip("statsmodels")
    analysis = ab.analyze_conversion(ab_df, "variant", "converted")
    bayes = analysis.bayesian
    assert bayes.prob_b_beats_a > 0.99
    assert bayes.lift_ci_low < bayes.expected_lift < bayes.lift_ci_high
    assert bayes.expected_lift == pytest.approx(analysis.absolute_lift, abs=0.01)


def test_bayesian_conversion_is_deterministic() -> None:
    a = ab.VariantStats("A", 1000, 100)
    b = ab.VariantStats("B", 1000, 130)
    first = ab.bayesian_conversion(a, b)
    second = ab.bayesian_conversion(a, b)
    assert first == second  # seeded Monte Carlo


def test_bayesian_no_effect_is_a_coin_flip() -> None:
    a = ab.VariantStats("A", 2000, 200)
    b = ab.VariantStats("B", 2000, 200)
    result = ab.bayesian_conversion(a, b)
    assert result.prob_b_beats_a == pytest.approx(0.5, abs=0.05)


def test_interim_look_warning_below_planned_n(ab_df: pd.DataFrame) -> None:
    pytest.importorskip("statsmodels")
    analysis = ab.analyze_conversion(ab_df, "variant", "converted", planned_n_per_variant=10_000)
    assert any("Interim look" in w for w in analysis.warnings)


def test_analyze_conversion_requires_two_variants(ab_df: pd.DataFrame) -> None:
    pytest.importorskip("statsmodels")
    df = ab_df[ab_df["variant"] == "A"]
    with pytest.raises(AnalysisError):
        ab.analyze_conversion(df, "variant", "converted")


def test_variant_and_metric_must_differ(ab_df: pd.DataFrame) -> None:
    # Validated before any heavy work — no statsmodels required.
    with pytest.raises(AnalysisError):
        ab.analyze_conversion(ab_df, "converted", "converted")


def test_required_sample_size_matches_published_ballpark() -> None:
    pytest.importorskip("statsmodels")
    # 10% baseline, +10% relative (10% → 11%), α=0.05, power=0.8:
    # standard calculators put this near ~14.7k per variant.
    n = ab.required_sample_size(0.10, 0.10)
    assert 13_000 < n < 16_500


def test_required_sample_size_validates_inputs() -> None:
    pytest.importorskip("statsmodels")
    with pytest.raises(AnalysisError):
        ab.required_sample_size(0.0, 0.1)
    with pytest.raises(AnalysisError):
        ab.required_sample_size(0.9, 0.5)  # target rate ≥ 100%


def test_log_experiment_run_snapshots_data(sqlite_env: Settings, ab_df: pd.DataFrame) -> None:
    pytest.importorskip("statsmodels")
    from app.services import analysis_service, experiment_service

    experiment_id = experiment_service.create_experiment("logged-exp")
    analysis = ab.analyze_conversion(ab_df, "variant", "converted")
    run_id = ab.log_experiment_run(
        ab_df, "ab-demo", analysis, experiment_id, variant_col="variant", outcome_col="converted"
    )
    assert run_id

    runs = analysis_service.list_recent_runs()
    assert runs[0].kind == "ab_test"
    assert runs[0].dataset_name == "ab-demo"
    assert experiment_service.list_experiments()[0].run_count == 1
