"""One named test per selector rule — the decision tree's executable specification."""

from __future__ import annotations

from typing import Any

import pytest

from app.core.errors import AnalysisError
from app.stats.profiler import GroupComparisonProfile, VarKind
from app.stats.test_selector import StatTest, recommend_test


def make_profile(**overrides: Any) -> GroupComparisonProfile:
    base: dict[str, Any] = {
        "outcome": "y",
        "group": "g",
        "outcome_kind": VarKind.CONTINUOUS,
        "n_groups": 2,
        "group_sizes": {"a": 100, "b": 100},
        "paired": False,
        "all_groups_normal": True,
        "equal_variances": True,
        "min_expected_count": None,
        "n_dropped_missing": 0,
    }
    base.update(overrides)
    return GroupComparisonProfile(**base)


# ── Continuous outcomes ─────────────────────────────────────────────────


def test_normal_equal_variance_selects_student_t() -> None:
    rec = recommend_test(make_profile())
    assert rec.test == StatTest.STUDENT_T
    assert rec.reasons  # rationale is part of the contract


def test_normal_unequal_variance_selects_welch_t() -> None:
    rec = recommend_test(make_profile(equal_variances=False))
    assert rec.test == StatTest.WELCH_T


def test_non_normal_large_groups_selects_welch_t_via_clt() -> None:
    rec = recommend_test(make_profile(all_groups_normal=False, group_sizes={"a": 60, "b": 45}))
    assert rec.test == StatTest.WELCH_T
    assert any("CLT" in r for r in rec.reasons)


def test_non_normal_small_groups_selects_mann_whitney() -> None:
    rec = recommend_test(make_profile(all_groups_normal=False, group_sizes={"a": 12, "b": 15}))
    assert rec.test == StatTest.MANN_WHITNEY_U


def test_paired_normal_selects_paired_t() -> None:
    rec = recommend_test(make_profile(paired=True))
    assert rec.test == StatTest.PAIRED_T


def test_paired_non_normal_selects_wilcoxon() -> None:
    rec = recommend_test(make_profile(paired=True, all_groups_normal=False))
    assert rec.test == StatTest.WILCOXON_SIGNED_RANK


def test_three_normal_equal_groups_select_anova() -> None:
    rec = recommend_test(make_profile(n_groups=3, group_sizes={"a": 50, "b": 50, "c": 50}))
    assert rec.test == StatTest.ONE_WAY_ANOVA


def test_three_normal_unequal_variance_groups_select_welch_anova() -> None:
    rec = recommend_test(
        make_profile(n_groups=3, group_sizes={"a": 50, "b": 50, "c": 50}, equal_variances=False)
    )
    assert rec.test == StatTest.WELCH_ANOVA


def test_three_non_normal_groups_select_kruskal() -> None:
    rec = recommend_test(
        make_profile(n_groups=3, group_sizes={"a": 50, "b": 50, "c": 50}, all_groups_normal=False)
    )
    assert rec.test == StatTest.KRUSKAL_WALLIS


# ── Ordinal outcomes (analyst-assigned) ─────────────────────────────────


def test_ordinal_two_groups_selects_mann_whitney() -> None:
    rec = recommend_test(
        make_profile(outcome_kind=VarKind.ORDINAL, all_groups_normal=None, equal_variances=None)
    )
    assert rec.test == StatTest.MANN_WHITNEY_U
    assert any("ordinal" in r for r in rec.reasons)


def test_ordinal_three_groups_selects_kruskal() -> None:
    rec = recommend_test(
        make_profile(
            outcome_kind=VarKind.ORDINAL,
            n_groups=3,
            group_sizes={"a": 40, "b": 40, "c": 40},
            all_groups_normal=None,
            equal_variances=None,
        )
    )
    assert rec.test == StatTest.KRUSKAL_WALLIS


def test_ordinal_paired_selects_wilcoxon() -> None:
    rec = recommend_test(
        make_profile(
            outcome_kind=VarKind.ORDINAL,
            paired=True,
            all_groups_normal=None,
            equal_variances=None,
        )
    )
    assert rec.test == StatTest.WILCOXON_SIGNED_RANK


# ── Binary / categorical outcomes ───────────────────────────────────────


def test_binary_outcome_two_groups_selects_two_proportion_z() -> None:
    rec = recommend_test(
        make_profile(
            outcome_kind=VarKind.BINARY,
            all_groups_normal=None,
            equal_variances=None,
            min_expected_count=40.0,
        )
    )
    assert rec.test == StatTest.TWO_PROPORTION_Z


def test_binary_outcome_small_expected_counts_selects_fisher() -> None:
    rec = recommend_test(
        make_profile(
            outcome_kind=VarKind.BINARY,
            group_sizes={"a": 12, "b": 9},
            all_groups_normal=None,
            equal_variances=None,
            min_expected_count=2.5,
        )
    )
    assert rec.test == StatTest.FISHER_EXACT


def test_binary_outcome_three_groups_selects_chi_square() -> None:
    rec = recommend_test(
        make_profile(
            outcome_kind=VarKind.BINARY,
            n_groups=3,
            group_sizes={"a": 40, "b": 40, "c": 40},
            all_groups_normal=None,
            equal_variances=None,
            min_expected_count=12.0,
        )
    )
    assert rec.test == StatTest.CHI_SQUARE


def test_categorical_outcome_selects_chi_square() -> None:
    rec = recommend_test(
        make_profile(
            outcome_kind=VarKind.CATEGORICAL,
            all_groups_normal=None,
            equal_variances=None,
            min_expected_count=8.0,
        )
    )
    assert rec.test == StatTest.CHI_SQUARE


# ── Guardrails ──────────────────────────────────────────────────────────


def test_datetime_outcome_raises() -> None:
    with pytest.raises(AnalysisError):
        recommend_test(make_profile(outcome_kind=VarKind.DATETIME))


def test_paired_three_groups_raises() -> None:
    with pytest.raises(AnalysisError):
        recommend_test(
            make_profile(paired=True, n_groups=3, group_sizes={"a": 10, "b": 10, "c": 10})
        )


def test_dropped_missing_rows_surface_as_warning() -> None:
    rec = recommend_test(make_profile(n_dropped_missing=7))
    assert any("7" in w for w in rec.warnings)
