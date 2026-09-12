"""Distribution fitting: ranking sanity, support guards, Q-Q data, candidate selection."""

from __future__ import annotations

import numpy as np
import pytest

from app.core.errors import AnalysisError
from app.stats.distributions import (
    CANDIDATES,
    fit_distributions,
    pdf_curve,
    qq_points,
    select_candidates,
)


def test_normal_sample_ranks_normal_family_on_top(rng: np.random.Generator) -> None:
    x = rng.normal(50.0, 5.0, 2000)
    fits = fit_distributions(x)
    assert fits, "expected at least one successful fit"
    # AIC discriminates weakly between normal and flexible 3-param families on
    # normal data — but the exponential must rank last by a wide margin.
    names = [f.name for f in fits]
    assert "normal" in names[:3]
    assert names[-1] == "exponential" or "exponential" not in names


def test_exponential_sample_prefers_exponential_over_normal(rng: np.random.Generator) -> None:
    x = rng.exponential(2.0, 2000)
    fits = fit_distributions(x)
    by_name = {f.name: f.aic for f in fits}
    assert by_name["exponential"] < by_name["normal"]


def test_positive_support_candidates_skip_negative_samples(rng: np.random.Generator) -> None:
    x = rng.normal(0.0, 1.0, 500)  # spans negative values
    names = {f.name for f in fit_distributions(x)}
    assert names == {"normal"}


def test_beta_only_fits_unit_interval(rng: np.random.Generator) -> None:
    inside = rng.beta(2.0, 5.0, 800)
    names_inside = {f.name for f in fit_distributions(inside)}
    assert "beta" in names_inside

    outside = rng.normal(10.0, 1.0, 800)
    names_outside = {f.name for f in fit_distributions(outside)}
    assert "beta" not in names_outside


def test_results_sorted_by_aic(rng: np.random.Generator) -> None:
    fits = fit_distributions(rng.lognormal(1.0, 0.4, 1000))
    aics = [f.aic for f in fits]
    assert aics == sorted(aics)


def test_too_few_observations_raises(rng: np.random.Generator) -> None:
    with pytest.raises(AnalysisError):
        fit_distributions(rng.normal(0, 1, 10))


def test_constant_sample_raises() -> None:
    with pytest.raises(AnalysisError):
        fit_distributions(np.full(100, 3.14))


def test_pdf_curve_matches_range(rng: np.random.Generator) -> None:
    fits = fit_distributions(rng.normal(0, 1, 500))
    xs, ys = pdf_curve(fits[0], -3.0, 3.0, n_points=100)
    assert xs.shape == ys.shape == (100,)
    assert xs[0] == pytest.approx(-3.0) and xs[-1] == pytest.approx(3.0)
    assert np.all(ys >= 0)


def test_qq_points_are_monotone(rng: np.random.Generator) -> None:
    x = rng.normal(0, 1, 400)
    fits = fit_distributions(x)
    theoretical, ordered = qq_points(fits[0], x)
    assert theoretical.shape == ordered.shape == (400,)
    assert np.all(np.diff(theoretical) >= 0)
    assert np.all(np.diff(ordered) >= 0)


def test_select_candidates_validates_names() -> None:
    subset = select_candidates(["normal", "gamma"])
    assert set(subset) == {"normal", "gamma"}
    with pytest.raises(AnalysisError):
        select_candidates(["normal", "cauchy"])
    with pytest.raises(AnalysisError):
        select_candidates([])
    assert set(select_candidates(list(CANDIDATES.keys()))) == set(CANDIDATES.keys())
