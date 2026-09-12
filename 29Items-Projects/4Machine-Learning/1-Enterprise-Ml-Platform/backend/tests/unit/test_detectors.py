"""Unit tests for the statistical drift detectors (pure functions)."""
from __future__ import annotations

import numpy as np

from src.ml.drift.detectors import (
    kl_divergence,
    ks_statistic,
    population_stability_index,
)

rng = np.random.default_rng(42)


def test_psi_near_zero_for_same_distribution() -> None:
    base = rng.normal(0, 1, 5000).tolist()
    curr = rng.normal(0, 1, 5000).tolist()
    assert population_stability_index(base, curr) < 0.1


def test_psi_large_for_shifted_distribution() -> None:
    base = rng.normal(0, 1, 5000).tolist()
    curr = rng.normal(3, 1, 5000).tolist()  # big mean shift
    assert population_stability_index(base, curr) > 0.2


def test_psi_handles_empty_inputs() -> None:
    assert population_stability_index([], [1.0, 2.0]) == 0.0
    assert population_stability_index([1.0, 2.0], []) == 0.0


def test_psi_handles_constant_baseline() -> None:
    # A degenerate single-value baseline must not raise (single-bin collapse).
    assert population_stability_index([5.0] * 100, [5.0] * 100) == 0.0


def test_ks_identical_is_zero_disjoint_is_one() -> None:
    assert ks_statistic([1, 2, 3, 4], [1, 2, 3, 4]) == 0.0
    assert ks_statistic([0, 0, 0], [9, 9, 9]) == 1.0


def test_kl_divergence_zero_for_equal_and_positive_otherwise() -> None:
    assert kl_divergence([1, 1, 1], [1, 1, 1]) == 0.0
    assert kl_divergence([0.9, 0.1], [0.1, 0.9]) > 0.0
    assert kl_divergence([], [1, 2]) == 0.0
