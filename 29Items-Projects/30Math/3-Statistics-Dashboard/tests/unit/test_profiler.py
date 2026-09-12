"""Profiler: column-kind classification and group-comparison profiling."""

from __future__ import annotations

import numpy as np
import pandas as pd
import pytest

from app.core.errors import AnalysisError
from app.stats.profiler import (
    VarKind,
    classify_column,
    profile_dataframe,
    profile_group_comparison,
)


class TestClassifyColumn:
    def test_continuous_floats(self) -> None:
        assert classify_column(pd.Series([1.2, 3.4, 5.6, 7.8, 9.1])) == VarKind.CONTINUOUS

    def test_binary_numeric(self) -> None:
        assert classify_column(pd.Series([0, 1, 1, 0, 1])) == VarKind.BINARY

    def test_binary_strings(self) -> None:
        assert classify_column(pd.Series(["yes", "no", "yes"])) == VarKind.BINARY

    def test_categorical_strings(self) -> None:
        series = pd.Series(["red", "green", "blue"] * 20)
        assert classify_column(series) == VarKind.CATEGORICAL

    def test_low_cardinality_integers_are_categorical(self) -> None:
        assert classify_column(pd.Series([1, 2, 3, 4, 5] * 10)) == VarKind.CATEGORICAL

    def test_id_like_strings(self) -> None:
        series = pd.Series([f"user-{i}" for i in range(100)])
        assert classify_column(series) == VarKind.ID

    def test_datetime(self) -> None:
        series = pd.Series(pd.to_datetime(["2026-01-01", "2026-01-02", "2026-01-03"]))
        assert classify_column(series) == VarKind.DATETIME


def test_profile_dataframe_covers_all_columns(ab_df: pd.DataFrame) -> None:
    profiles = profile_dataframe(ab_df)
    assert [p.name for p in profiles] == list(ab_df.columns)
    by_name = {p.name: p for p in profiles}
    assert by_name["variant"].kind == VarKind.BINARY
    assert by_name["converted"].kind == VarKind.BINARY
    assert by_name["session_minutes"].kind == VarKind.CONTINUOUS
    assert by_name["age_group"].kind == VarKind.CATEGORICAL


def test_group_comparison_profile_continuous(ab_df: pd.DataFrame) -> None:
    profile = profile_group_comparison(ab_df, "session_minutes", "variant")
    assert profile.n_groups == 2
    assert profile.group_sizes == {"A": 400, "B": 400}
    assert profile.outcome_kind == VarKind.CONTINUOUS
    # Assumption checks ran (not None); their verdict on random draws is sampling
    # noise (~5% false rejection by design), so it is asserted only in the
    # by-construction test below.
    assert profile.all_groups_normal is not None
    assert profile.equal_variances is not None
    assert profile.min_expected_count is None


def test_assumption_checks_on_shifted_copy(rng: np.random.Generator) -> None:
    """A group and its shifted copy have identical spread by construction:
    Levene's statistic is exactly 0 (p=1) and normality verdicts match."""
    base = rng.normal(10.0, 2.0, 200)
    df = pd.DataFrame({"y": np.concatenate([base, base + 1.0]), "g": ["A"] * 200 + ["B"] * 200})
    profile = profile_group_comparison(df, "y", "g")
    assert profile.equal_variances is True
    assert profile.all_groups_normal is True


def test_group_comparison_profile_binary_outcome(ab_df: pd.DataFrame) -> None:
    profile = profile_group_comparison(ab_df, "converted", "variant")
    assert profile.outcome_kind == VarKind.BINARY
    assert profile.min_expected_count is not None
    assert profile.all_groups_normal is None


def test_ordinal_override_on_numeric_column(ab_df: pd.DataFrame) -> None:
    df = ab_df.assign(satisfaction=(ab_df["converted"] * 2 + 1))  # int-coded scale
    profile = profile_group_comparison(
        df, "satisfaction", "variant", outcome_kind_override=VarKind.ORDINAL
    )
    assert profile.outcome_kind == VarKind.ORDINAL
    assert profile.all_groups_normal is None  # rank-based path: no normality checks


def test_ordinal_override_rejected_on_strings(ab_df: pd.DataFrame) -> None:
    with pytest.raises(AnalysisError):
        profile_group_comparison(
            ab_df, "age_group", "variant", outcome_kind_override=VarKind.ORDINAL
        )


def test_non_overridable_kind_rejected(ab_df: pd.DataFrame) -> None:
    with pytest.raises(AnalysisError):
        profile_group_comparison(
            ab_df, "session_minutes", "variant", outcome_kind_override=VarKind.ID
        )


def test_single_group_raises() -> None:
    df = pd.DataFrame({"y": np.arange(10, dtype=float), "g": ["only"] * 10})
    with pytest.raises(AnalysisError):
        profile_group_comparison(df, "y", "g")


def test_missing_column_raises(ab_df: pd.DataFrame) -> None:
    with pytest.raises(AnalysisError):
        profile_group_comparison(ab_df, "nope", "variant")


def test_same_column_for_outcome_and_group_raises(ab_df: pd.DataFrame) -> None:
    with pytest.raises(AnalysisError):
        profile_group_comparison(ab_df, "variant", "variant")
