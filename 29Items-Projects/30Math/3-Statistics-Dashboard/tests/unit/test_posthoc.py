"""Holm correction and pairwise follow-ups."""

from __future__ import annotations

import numpy as np
import pandas as pd
import pytest

from app.core.errors import AnalysisError
from app.stats.posthoc import holm_adjust, pairwise_comparisons
from app.stats.profiler import VarKind


class TestHolmAdjust:
    def test_known_values(self) -> None:
        # Classic example: sorted p (0.01, 0.02, 0.04) with m=3
        # -> adjusted (0.03, 0.04, 0.04) after the monotonicity step.
        adjusted = holm_adjust([0.04, 0.01, 0.02])
        assert adjusted == pytest.approx([0.04, 0.03, 0.04])

    def test_clipped_at_one(self) -> None:
        assert max(holm_adjust([0.9, 0.8, 0.7])) <= 1.0

    def test_single_p_unchanged(self) -> None:
        assert holm_adjust([0.03]) == pytest.approx([0.03])

    def test_empty(self) -> None:
        assert holm_adjust([]) == []

    def test_adjusted_never_below_raw(self) -> None:
        raw = [0.001, 0.02, 0.3, 0.04, 0.7]
        for raw_p, adj_p in zip(raw, holm_adjust(raw), strict=False):
            assert adj_p >= raw_p


def three_group_df(rng: np.random.Generator) -> pd.DataFrame:
    return pd.DataFrame(
        {
            "y": np.concatenate(
                [rng.normal(0, 1, 60), rng.normal(0, 1, 60), rng.normal(1.5, 1, 60)]
            ),
            "g": ["a"] * 60 + ["b"] * 60 + ["c"] * 60,
        }
    )


def test_pairwise_parametric_finds_the_shifted_group(rng: np.random.Generator) -> None:
    results = pairwise_comparisons(
        three_group_df(rng), "y", "g", outcome_kind=VarKind.CONTINUOUS, parametric=True
    )
    assert len(results) == 3  # a-b, a-c, b-c
    by_pair = {(r.group_a, r.group_b): r for r in results}
    assert not by_pair[("a", "b")].significant  # same distribution
    assert by_pair[("a", "c")].significant
    assert by_pair[("b", "c")].significant


def test_pairwise_nonparametric_runs(rng: np.random.Generator) -> None:
    results = pairwise_comparisons(
        three_group_df(rng), "y", "g", outcome_kind=VarKind.CONTINUOUS, parametric=False
    )
    assert all(0.0 <= r.p_adjusted <= 1.0 for r in results)


def test_pairwise_binary_outcome_uses_chi_square(rng: np.random.Generator) -> None:
    df = pd.DataFrame(
        {
            "converted": np.concatenate(
                [rng.binomial(1, 0.10, 200), rng.binomial(1, 0.11, 200), rng.binomial(1, 0.45, 200)]
            ),
            "g": ["a"] * 200 + ["b"] * 200 + ["c"] * 200,
        }
    )
    results = pairwise_comparisons(
        df, "converted", "g", outcome_kind=VarKind.BINARY, parametric=False
    )
    by_pair = {(r.group_a, r.group_b): r for r in results}
    assert by_pair[("a", "c")].significant
    assert not by_pair[("a", "b")].significant


def test_pairwise_requires_three_groups(ab_df: pd.DataFrame) -> None:
    with pytest.raises(AnalysisError):
        pairwise_comparisons(
            ab_df, "session_minutes", "variant", outcome_kind=VarKind.CONTINUOUS, parametric=True
        )
