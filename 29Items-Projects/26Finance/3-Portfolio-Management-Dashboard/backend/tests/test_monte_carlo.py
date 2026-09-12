from __future__ import annotations

import numpy as np

from app.analytics.monte_carlo import simulate_portfolio


def test_result_shapes_and_reproducibility(mu_cov):
    mu, cov, _ = mu_cov
    weights = np.full(len(mu), 1.0 / len(mu))
    a = simulate_portfolio(mu, cov, weights, n_days=252, n_sims=2000, seed=123)
    b = simulate_portfolio(mu, cov, weights, n_days=252, n_sims=2000, seed=123)

    assert len(a.band_p50) == 252 + 1
    assert len(a.time_axis) == 252 + 1
    # Same seed -> identical results.
    assert a.var == b.var
    assert a.expected_terminal_value == b.expected_terminal_value


def test_cvar_dominates_var(mu_cov):
    mu, cov, _ = mu_cov
    weights = np.full(len(mu), 1.0 / len(mu))
    res = simulate_portfolio(mu, cov, weights, n_days=126, n_sims=5000, seed=1)
    assert res.cvar >= res.var
    assert 0.0 <= res.prob_loss <= 1.0


def test_low_volatility_rarely_loses():
    # Strong positive drift, negligible variance -> almost surely a gain.
    mu = np.array([0.15, 0.15])
    cov = np.array([[1e-6, 0.0], [0.0, 1e-6]])
    weights = np.array([0.5, 0.5])
    res = simulate_portfolio(mu, cov, weights, n_days=252, n_sims=3000, seed=5)
    assert res.prob_loss < 0.05
    assert res.expected_terminal_value > res.initial_value
