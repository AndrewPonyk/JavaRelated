"""Automated statistical test selection — the product's core business logic.

A transparent decision tree over ``GroupComparisonProfile``. Every branch appends
a human-readable reason; the UI shows the rationale verbatim (trust is a feature).
Pure and deterministic: profile in, recommendation out.

Selection rules (mirrored 1:1 by tests/unit/test_test_selector.py):

    outcome BINARY:
        2 groups, expected counts ok      -> TWO_PROPORTION_Z   (fallback CHI_SQUARE)
        2 groups, min expected < 5        -> FISHER_EXACT
        k > 2 groups                      -> CHI_SQUARE
    outcome CATEGORICAL:
        2 groups, min expected < 5        -> FISHER_EXACT (2x2 only)
        otherwise                         -> CHI_SQUARE (+ warning when cells sparse)
    outcome ORDINAL (analyst-assigned, rank-based by definition):
        paired                            -> WILCOXON_SIGNED_RANK
        2 groups                          -> MANN_WHITNEY_U
        k > 2 groups                      -> KRUSKAL_WALLIS
    outcome CONTINUOUS:
        paired, normal                    -> PAIRED_T
        paired, non-normal                -> WILCOXON_SIGNED_RANK
        2 groups, normal, equal var       -> STUDENT_T
        2 groups, normal, unequal var     -> WELCH_T
        2 groups, non-normal, n >= 30     -> WELCH_T (CLT)
        2 groups, non-normal, n < 30      -> MANN_WHITNEY_U
        k > 2, normal, equal var          -> ONE_WAY_ANOVA
        k > 2, normal, unequal var        -> WELCH_ANOVA
        k > 2, non-normal                 -> KRUSKAL_WALLIS
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from app.core.errors import AnalysisError
from app.stats.profiler import CLT_MIN_N, GroupComparisonProfile, VarKind


class StatTest(str, Enum):
    STUDENT_T = "student_t"
    WELCH_T = "welch_t"
    PAIRED_T = "paired_t"
    MANN_WHITNEY_U = "mann_whitney_u"
    WILCOXON_SIGNED_RANK = "wilcoxon_signed_rank"
    ONE_WAY_ANOVA = "one_way_anova"
    WELCH_ANOVA = "welch_anova"
    KRUSKAL_WALLIS = "kruskal_wallis"
    CHI_SQUARE = "chi_square"
    FISHER_EXACT = "fisher_exact"
    TWO_PROPORTION_Z = "two_proportion_z"


LABELS: dict[StatTest, str] = {
    StatTest.STUDENT_T: "Student's t-test (independent samples)",
    StatTest.WELCH_T: "Welch's t-test (unequal variances)",
    StatTest.PAIRED_T: "Paired t-test",
    StatTest.MANN_WHITNEY_U: "Mann-Whitney U test",
    StatTest.WILCOXON_SIGNED_RANK: "Wilcoxon signed-rank test",
    StatTest.ONE_WAY_ANOVA: "One-way ANOVA",
    StatTest.WELCH_ANOVA: "Welch's ANOVA",
    StatTest.KRUSKAL_WALLIS: "Kruskal-Wallis H test",
    StatTest.CHI_SQUARE: "Chi-square test of independence",
    StatTest.FISHER_EXACT: "Fisher's exact test",
    StatTest.TWO_PROPORTION_Z: "Two-proportion z-test",
}

MIN_EXPECTED_FOR_CHI2 = 5.0


@dataclass(frozen=True)
class TestRecommendation:
    test: StatTest
    reasons: tuple[str, ...]
    warnings: tuple[str, ...] = ()
    fallbacks: tuple[StatTest, ...] = ()

    @property
    def label(self) -> str:
        return LABELS[self.test]


def recommend_test(
    p: GroupComparisonProfile,
) -> TestRecommendation:  # — an explicit tree beats clever dispatch here
    reasons: list[str] = []
    warnings: list[str] = []

    if p.n_dropped_missing:
        warnings.append(f"{p.n_dropped_missing} row(s) with missing values were excluded.")

    if p.outcome_kind in (VarKind.DATETIME, VarKind.ID):
        raise AnalysisError(
            f"outcome '{p.outcome}' is {p.outcome_kind.value}",
            user_message=(
                f"'{p.outcome}' looks like a {p.outcome_kind.value} column — "
                "pick a numeric or categorical outcome."
            ),
        )

    # ── Binary outcome (e.g. converted 0/1) ─────────────────────────────
    if p.outcome_kind == VarKind.BINARY:
        reasons.append(f"Outcome '{p.outcome}' is binary → comparing proportions.")
        if p.n_groups == 2:
            if p.min_expected_count is not None and p.min_expected_count < MIN_EXPECTED_FOR_CHI2:
                reasons.append(
                    f"Smallest expected cell count is {p.min_expected_count:.1f} "
                    f"(< {MIN_EXPECTED_FOR_CHI2:g}) → exact test required."
                )
                return TestRecommendation(
                    StatTest.FISHER_EXACT, tuple(reasons), tuple(warnings), (StatTest.CHI_SQUARE,)
                )
            reasons.append("Two groups with adequate expected counts → two-proportion z-test.")
            return TestRecommendation(
                StatTest.TWO_PROPORTION_Z, tuple(reasons), tuple(warnings), (StatTest.CHI_SQUARE,)
            )
        reasons.append(f"{p.n_groups} groups → chi-square test across proportions.")
        return TestRecommendation(StatTest.CHI_SQUARE, tuple(reasons), tuple(warnings))

    # ── Categorical outcome ──────────────────────────────────────────────
    if p.outcome_kind == VarKind.CATEGORICAL:
        reasons.append(f"Outcome '{p.outcome}' is categorical → test of independence.")
        if p.min_expected_count is not None and p.min_expected_count < MIN_EXPECTED_FOR_CHI2:
            if p.n_groups == 2:
                reasons.append("Small expected cell counts → Fisher's exact test.")
                return TestRecommendation(
                    StatTest.FISHER_EXACT, tuple(reasons), tuple(warnings), (StatTest.CHI_SQUARE,)
                )
            warnings.append(
                "Some expected cell counts are below 5 — chi-square p-values are approximate; "
                "consider collapsing sparse categories."
            )
        return TestRecommendation(StatTest.CHI_SQUARE, tuple(reasons), tuple(warnings))

    # ── Ordinal outcome (assigned by the analyst — rank-based tests only) ──
    if p.outcome_kind == VarKind.ORDINAL:
        reasons.append(
            f"Outcome '{p.outcome}' is ordinal → rank-based tests (means are undefined "
            "on an ordinal scale)."
        )
        if p.paired:
            if p.n_groups != 2:
                raise AnalysisError(
                    "paired comparison requires exactly 2 groups",
                    user_message="Paired comparisons need exactly two groups.",
                )
            reasons.append("Two paired groups → Wilcoxon signed-rank test.")
            return TestRecommendation(
                StatTest.WILCOXON_SIGNED_RANK, tuple(reasons), tuple(warnings)
            )
        if p.n_groups == 2:
            reasons.append("Two independent groups → Mann-Whitney U.")
            return TestRecommendation(StatTest.MANN_WHITNEY_U, tuple(reasons), tuple(warnings))
        reasons.append(f"{p.n_groups} independent groups → Kruskal-Wallis H.")
        return TestRecommendation(StatTest.KRUSKAL_WALLIS, tuple(reasons), tuple(warnings))

    # ── Continuous outcome ───────────────────────────────────────────────
    reasons.append(f"Outcome '{p.outcome}' is continuous.")

    if p.paired:
        if p.n_groups != 2:
            raise AnalysisError(
                "paired comparison requires exactly 2 groups",
                user_message="Paired comparisons need exactly two groups.",
            )
        if p.all_groups_normal:
            reasons.append("Two paired groups with approximately normal values → paired t-test.")
            return TestRecommendation(
                StatTest.PAIRED_T, tuple(reasons), tuple(warnings), (StatTest.WILCOXON_SIGNED_RANK,)
            )
        reasons.append("Two paired groups; normality rejected → Wilcoxon signed-rank test.")
        return TestRecommendation(StatTest.WILCOXON_SIGNED_RANK, tuple(reasons), tuple(warnings))

    if p.n_groups == 2:
        if p.all_groups_normal:
            reasons.append("Both groups look approximately normal (Shapiro-Wilk).")
            if p.equal_variances:
                reasons.append("Variances look homogeneous (Levene) → Student's t-test.")
                return TestRecommendation(
                    StatTest.STUDENT_T, tuple(reasons), tuple(warnings), (StatTest.WELCH_T,)
                )
            reasons.append("Variance homogeneity rejected (Levene) → Welch's t-test.")
            return TestRecommendation(StatTest.WELCH_T, tuple(reasons), tuple(warnings))
        if p.min_group_size >= CLT_MIN_N:
            reasons.append(
                f"Normality rejected, but every group has n ≥ {CLT_MIN_N} → "
                "the CLT makes Welch's t-test robust here."
            )
            return TestRecommendation(
                StatTest.WELCH_T, tuple(reasons), tuple(warnings), (StatTest.MANN_WHITNEY_U,)
            )
        reasons.append(
            f"Normality rejected and the smallest group has n = {p.min_group_size} "
            f"(< {CLT_MIN_N}) → Mann-Whitney U."
        )
        return TestRecommendation(StatTest.MANN_WHITNEY_U, tuple(reasons), tuple(warnings))

    # k > 2 independent groups
    if p.all_groups_normal and p.equal_variances:
        reasons.append(
            f"{p.n_groups} approximately normal groups with homogeneous variances → one-way ANOVA."
        )
        warnings.append(
            "A significant omnibus result triggers Holm-corrected pairwise follow-ups "
            "(shown below the result card)."
        )
        return TestRecommendation(
            StatTest.ONE_WAY_ANOVA, tuple(reasons), tuple(warnings), (StatTest.KRUSKAL_WALLIS,)
        )
    if p.all_groups_normal:
        reasons.append(
            f"{p.n_groups} normal groups but variance homogeneity rejected → Welch's ANOVA."
        )
        return TestRecommendation(
            StatTest.WELCH_ANOVA, tuple(reasons), tuple(warnings), (StatTest.KRUSKAL_WALLIS,)
        )
    reasons.append(f"{p.n_groups} groups; normality rejected → Kruskal-Wallis H.")
    return TestRecommendation(StatTest.KRUSKAL_WALLIS, tuple(reasons), tuple(warnings))
