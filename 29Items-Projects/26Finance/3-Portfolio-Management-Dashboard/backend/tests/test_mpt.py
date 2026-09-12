from __future__ import annotations

import numpy as np

from app.analytics.mpt import (
    efficient_frontier,
    global_min_variance_closed_form,
    maximum_sharpe_portfolio,
    minimum_variance_portfolio,
    portfolio_sharpe,
    portfolio_volatility,
)


def test_min_variance_weights_are_valid(mu_cov):
    mu, cov, labels = mu_cov
    mv = minimum_variance_portfolio(mu, cov, labels=labels)
    np.testing.assert_allclose(mv.weights.sum(), 1.0, atol=1e-6)
    assert np.all(mv.weights >= -1e-6)  # long-only


def test_min_variance_beats_equal_weight(mu_cov):
    mu, cov, _ = mu_cov
    equal = np.full(len(mu), 1.0 / len(mu))
    mv = minimum_variance_portfolio(mu, cov)
    assert mv.volatility <= portfolio_volatility(equal, cov) + 1e-9


def test_unconstrained_matches_closed_form(mu_cov):
    mu, cov, _ = mu_cov
    # Loose bounds so the inequality constraints don't bind -> ~ unconstrained.
    mv = minimum_variance_portfolio(mu, cov, bounds=(-5.0, 5.0))
    closed = global_min_variance_closed_form(cov)
    np.testing.assert_allclose(mv.weights, closed, atol=1e-2)


def test_max_sharpe_beats_equal_weight(mu_cov):
    mu, cov, _ = mu_cov
    rf = 0.02
    equal = np.full(len(mu), 1.0 / len(mu))
    ms = maximum_sharpe_portfolio(mu, cov, risk_free_rate=rf)
    assert ms.sharpe >= portfolio_sharpe(equal, mu, cov, rf) - 1e-6


def test_efficient_frontier_is_monotonic(mu_cov):
    mu, cov, labels = mu_cov
    frontier = efficient_frontier(mu, cov, n_points=25, labels=labels)
    assert len(frontier) >= 2
    returns = [p.expected_return for p in frontier]
    # Targets are swept low -> high, so returns are non-decreasing.
    assert all(b >= a - 1e-6 for a, b in zip(returns, returns[1:], strict=False))
    # Every frontier portfolio is fully invested.
    for p in frontier:
        np.testing.assert_allclose(p.weights.sum(), 1.0, atol=1e-6)
