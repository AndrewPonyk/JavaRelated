"""Test runners: known-answer checks on seeded synthetic data.

Strong assertions only where the effect is unambiguous (shifted distributions);
structural assertions elsewhere — borderline p-value asserts are flaky by construction.
"""

from __future__ import annotations

import numpy as np
import pandas as pd
import pytest

from app.core.errors import AnalysisError
from app.stats.hypothesis_tests import run_test
from app.stats.test_selector import StatTest


def two_group_df(a: np.ndarray, b: np.ndarray) -> pd.DataFrame:
    return pd.DataFrame({"y": np.concatenate([a, b]), "g": ["a"] * len(a) + ["b"] * len(b)})


def test_student_t_detects_a_large_shift(rng: np.random.Generator) -> None:
    df = two_group_df(rng.normal(0.0, 1.0, 100), rng.normal(1.5, 1.0, 100))
    result = run_test(StatTest.STUDENT_T, df, "y", "g", alpha=0.05)
    assert result.p_value < 1e-6
    assert result.significant
    assert result.effect_size is not None
    assert abs(result.effect_size.value) > 0.8  # |Cohen's d| ≈ 1.5 → "large"
    assert result.effect_size.magnitude == "large"
    assert "Reject" in result.interpretation


def test_welch_t_reports_satterthwaite_df(rng: np.random.Generator) -> None:
    df = two_group_df(rng.normal(0, 1.0, 80), rng.normal(0.2, 3.0, 40))
    result = run_test(StatTest.WELCH_T, df, "y", "g")
    assert result.df is not None
    assert 0 < result.df < 118  # Welch df is below the pooled df with unequal variances
    assert 0.0 <= result.p_value <= 1.0


def test_mann_whitney_on_skewed_data(rng: np.random.Generator) -> None:
    df = two_group_df(rng.exponential(1.0, 60), rng.exponential(3.0, 60))
    result = run_test(StatTest.MANN_WHITNEY_U, df, "y", "g")
    assert result.p_value < 0.01
    assert result.effect_size is not None  # rank-biserial r


def test_anova_three_groups_one_shifted(rng: np.random.Generator) -> None:
    df = pd.DataFrame(
        {
            "y": np.concatenate(
                [rng.normal(0, 1, 50), rng.normal(0, 1, 50), rng.normal(1.2, 1, 50)]
            ),
            "g": ["a"] * 50 + ["b"] * 50 + ["c"] * 50,
        }
    )
    result = run_test(StatTest.ONE_WAY_ANOVA, df, "y", "g")
    assert result.p_value < 0.001
    assert result.effect_size is not None
    assert result.effect_size.value > 0  # η² is non-negative


def test_chi_square_structure(ab_df: pd.DataFrame) -> None:
    result = run_test(StatTest.CHI_SQUARE, ab_df, "age_group", "variant")
    assert 0.0 <= result.p_value <= 1.0
    assert result.df is not None
    assert result.effect_size is not None  # Cramér's V


def test_two_proportion_z_detects_conversion_lift(ab_df: pd.DataFrame) -> None:
    pytest.importorskip("statsmodels")
    result = run_test(StatTest.TWO_PROPORTION_Z, ab_df, "converted", "variant")
    assert result.p_value < 0.01  # 10% vs 18% at n=400/group is unambiguous
    assert result.effect_size is not None
    assert result.effect_size.value > 0.0  # B − A lift is positive


def test_zero_variance_raises_instead_of_nan() -> None:
    df = pd.DataFrame({"y": [5.0] * 20, "g": ["a"] * 10 + ["b"] * 10})
    with pytest.raises(AnalysisError):
        run_test(StatTest.STUDENT_T, df, "y", "g")


def test_paired_t_requires_equal_sizes(rng: np.random.Generator) -> None:
    df = two_group_df(rng.normal(0, 1, 30), rng.normal(0, 1, 25))
    with pytest.raises(AnalysisError):
        run_test(StatTest.PAIRED_T, df, "y", "g")


def test_result_serializes_for_persistence(rng: np.random.Generator) -> None:
    df = two_group_df(rng.normal(0, 1, 50), rng.normal(1, 1, 50))
    payload = run_test(StatTest.STUDENT_T, df, "y", "g").to_json_dict()
    assert payload["test"] == "student_t"
    assert isinstance(payload["p_value"], float)
    assert payload["effect_size"] is not None
