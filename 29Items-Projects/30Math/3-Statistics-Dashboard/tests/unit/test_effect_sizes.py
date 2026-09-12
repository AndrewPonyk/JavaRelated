"""Effect sizes checked against hand-computed values."""

from __future__ import annotations

import numpy as np
import pytest

from app.stats import effect_sizes as es


def test_cohens_d_known_value() -> None:
    a = np.array([1.0, 2.0, 3.0, 4.0, 5.0])
    b = np.array([3.0, 4.0, 5.0, 6.0, 7.0])
    # mean diff = -2, pooled sd = sqrt(2.5) ≈ 1.5811 → d ≈ -1.2649
    result = es.cohens_d(a, b)
    assert result.value == pytest.approx(-1.2649, abs=1e-3)
    assert result.magnitude == "large"


def test_hedges_g_shrinks_toward_zero() -> None:
    a = np.array([1.0, 2.0, 3.0, 4.0, 5.0])
    b = np.array([3.0, 4.0, 5.0, 6.0, 7.0])
    d = es.cohens_d(a, b).value
    g = es.hedges_g(a, b).value
    assert abs(g) < abs(d)
    assert np.sign(g) == np.sign(d)


def test_rank_biserial_extremes() -> None:
    assert es.rank_biserial_from_u(0.0, 10, 10).value == pytest.approx(1.0)
    assert es.rank_biserial_from_u(50.0, 10, 10).value == pytest.approx(0.0)


def test_cliffs_delta_complete_separation() -> None:
    a = np.array([10.0, 11.0, 12.0])
    b = np.array([1.0, 2.0, 3.0])
    assert es.cliffs_delta(a, b).value == pytest.approx(1.0)
    assert es.cliffs_delta(b, a).value == pytest.approx(-1.0)


def test_cramers_v_bounds() -> None:
    assert es.cramers_v(0.0, 100, 2, 2).value == pytest.approx(0.0)
    v = es.cramers_v(25.0, 100, 2, 2).value
    assert 0.0 < v <= 1.0


def test_eta_squared_identical_groups_is_zero() -> None:
    g = np.array([1.0, 2.0, 3.0, 4.0])
    result = es.eta_squared([g, g.copy(), g.copy()])
    assert result.value == pytest.approx(0.0, abs=1e-12)


def test_zero_variance_yields_unknown_not_crash() -> None:
    a = np.array([2.0, 2.0, 2.0])
    result = es.cohens_d(a, a)
    assert result.magnitude == "unknown"
