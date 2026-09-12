"""Data profiling: derive the characteristics that drive automated test selection."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

import numpy as np
import pandas as pd
from pandas.api import types as ptypes
from scipy import stats

from app.core.errors import AnalysisError

#: Shapiro-Wilk is slow and hypersensitive at large n — subsample above this
#: (docs/TECH-NOTES.md §3.6.8).
NORMALITY_MAX_N = 5_000

#: Below this per-group size, normality tests are underpowered and CLT does not apply.
CLT_MIN_N = 30

#: A numeric integer column with at most this many distinct values reads as categorical.
FEW_DISTINCT = 10

#: Object columns whose unique ratio exceeds this are identifiers, not categories.
ID_UNIQUE_RATIO = 0.90


class VarKind(str, Enum):
    CONTINUOUS = "continuous"
    BINARY = "binary"
    CATEGORICAL = "categorical"
    ORDINAL = "ordinal"
    DATETIME = "datetime"
    ID = "id"


#: ORDINAL is never auto-detected: int-coded scales (Likert 1–5) are statistically
#: indistinguishable from counts, so guessing would be wrong silently. Analysts
#: assign it explicitly via the Data Explorer's column-kind override, and it is
#: only valid for numeric columns (rank order must be well-defined).
OVERRIDABLE_KINDS = (VarKind.CONTINUOUS, VarKind.BINARY, VarKind.CATEGORICAL, VarKind.ORDINAL)


@dataclass(frozen=True)
class ColumnProfile:
    name: str
    kind: VarKind
    dtype: str
    n_missing: int
    n_unique: int


@dataclass(frozen=True)
class GroupComparisonProfile:
    """Characteristics of ``outcome ~ group`` that the test selector consumes."""

    outcome: str
    group: str
    outcome_kind: VarKind
    n_groups: int
    group_sizes: dict[str, int]
    paired: bool
    all_groups_normal: bool | None  # None when outcome is not continuous
    equal_variances: bool | None  # None when outcome is not continuous
    min_expected_count: float | None  # None when outcome is not categorical/binary
    n_dropped_missing: int

    @property
    def min_group_size(self) -> int:
        return min(self.group_sizes.values())


def classify_column(series: pd.Series) -> VarKind:
    """Heuristic column-kind classification (CSV type inference lies — TECH-NOTES §3.6.14)."""
    non_null = series.dropna()
    if non_null.empty:
        return VarKind.ID  # unusable either way; excluded from analysis
    n_unique = int(non_null.nunique())

    if ptypes.is_datetime64_any_dtype(series):
        return VarKind.DATETIME
    if ptypes.is_bool_dtype(series) or n_unique == 2:
        return VarKind.BINARY
    if ptypes.is_numeric_dtype(series):
        if ptypes.is_integer_dtype(series) and n_unique <= FEW_DISTINCT:
            return VarKind.CATEGORICAL
        return VarKind.CONTINUOUS
    if n_unique / len(non_null) > ID_UNIQUE_RATIO:
        return VarKind.ID
    return VarKind.CATEGORICAL


def profile_dataframe(df: pd.DataFrame) -> list[ColumnProfile]:
    return [
        ColumnProfile(
            name=str(col),
            kind=classify_column(df[col]),
            dtype=str(df[col].dtype),
            n_missing=int(df[col].isna().sum()),
            n_unique=int(df[col].nunique()),
        )
        for col in df.columns
    ]


def check_normality(x: np.ndarray, alpha: float = 0.05, *, seed: int = 0) -> bool:
    """Shapiro-Wilk on (a subsample of) x. True when normality is NOT rejected."""
    arr = np.asarray(x, dtype=float)
    arr = arr[~np.isnan(arr)]
    if arr.size < 3:
        return False  # cannot establish normality — force the conservative branch
    if np.ptp(arr) == 0:
        return False  # constant data
    if arr.size > NORMALITY_MAX_N:
        rng = np.random.default_rng(seed)
        arr = rng.choice(arr, size=NORMALITY_MAX_N, replace=False)
    _, p_value = stats.shapiro(arr)
    return bool(p_value >= alpha)


def check_equal_variances(groups: list[np.ndarray], alpha: float = 0.05) -> bool:
    """Levene's test (median-centered — robust). True when homogeneity is NOT rejected."""
    cleaned = [np.asarray(g, dtype=float) for g in groups]
    cleaned = [g[~np.isnan(g)] for g in cleaned]
    if any(g.size < 2 for g in cleaned):
        return False
    _, p_value = stats.levene(*cleaned, center="median")
    return bool(p_value >= alpha)


def profile_group_comparison(
    df: pd.DataFrame,
    outcome: str,
    group: str,
    *,
    paired: bool = False,
    alpha: float = 0.05,
    outcome_kind_override: VarKind | None = None,
) -> GroupComparisonProfile:
    """Build the profile the test selector consumes. Raises AnalysisError on unusable input.

    ``outcome_kind_override`` lets the analyst correct the heuristic (e.g. mark an
    int-coded Likert column ORDINAL) — set via the Data Explorer.
    """
    if outcome == group:
        raise AnalysisError(
            "outcome equals group",
            user_message="Pick two different columns for the outcome and the group.",
        )
    for col in (outcome, group):
        if col not in df.columns:
            raise AnalysisError(
                f"column '{col}' not found",
                user_message=f"Column '{col}' is not in the dataset.",
            )

    frame = df[[outcome, group]].dropna()
    n_dropped = len(df) - len(frame)

    levels = frame[group].unique().tolist()
    if len(levels) < 2:
        raise AnalysisError(
            f"grouping column '{group}' has {len(levels)} level(s)",
            user_message=f"'{group}' needs at least two groups to compare.",
        )

    group_sizes = {str(lvl): int(cnt) for lvl, cnt in frame[group].value_counts().items()}
    if min(group_sizes.values()) < 2:
        raise AnalysisError(
            "a group has fewer than 2 observations",
            user_message="Each group needs at least 2 observations.",
        )

    if outcome_kind_override is not None:
        if outcome_kind_override not in OVERRIDABLE_KINDS:
            raise AnalysisError(
                f"kind {outcome_kind_override.value} is not overridable",
                user_message="Only continuous, binary, categorical, or ordinal can be assigned.",
            )
        from pandas.api import types as _ptypes

        if outcome_kind_override == VarKind.ORDINAL and not _ptypes.is_numeric_dtype(
            frame[outcome]
        ):
            raise AnalysisError(
                "ordinal override on non-numeric column",
                user_message=(
                    f"'{outcome}' is not numeric — encode the scale as numbers before "
                    "marking it ordinal."
                ),
            )
        outcome_kind = outcome_kind_override
    else:
        outcome_kind = classify_column(frame[outcome])

    all_normal: bool | None = None
    equal_var: bool | None = None
    min_expected: float | None = None

    if outcome_kind == VarKind.CONTINUOUS:
        arrays = [frame.loc[frame[group] == lvl, outcome].to_numpy(dtype=float) for lvl in levels]
        all_normal = all(check_normality(a, alpha) for a in arrays)
        equal_var = check_equal_variances(arrays, alpha)
    elif outcome_kind in (VarKind.BINARY, VarKind.CATEGORICAL):
        contingency = pd.crosstab(frame[group], frame[outcome])
        expected = stats.contingency.expected_freq(contingency.to_numpy())
        min_expected = float(expected.min())

    return GroupComparisonProfile(
        outcome=outcome,
        group=group,
        outcome_kind=outcome_kind,
        n_groups=len(levels),
        group_sizes=group_sizes,
        paired=paired,
        all_groups_normal=all_normal,
        equal_variances=equal_var,
        min_expected_count=min_expected,
        n_dropped_missing=n_dropped,
    )
