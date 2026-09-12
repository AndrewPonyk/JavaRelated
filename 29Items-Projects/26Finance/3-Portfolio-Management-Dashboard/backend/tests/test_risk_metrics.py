from __future__ import annotations

import numpy as np

from app.analytics.risk_metrics import (
    conditional_value_at_risk,
    max_drawdown,
    sharpe_ratio,
    summary,
    value_at_risk,
)


def _normal_returns(seed: int = 1, n: int = 2000) -> np.ndarray:
    rng = np.random.default_rng(seed)
    return rng.normal(0.0005, 0.01, n)


def test_var_is_positive_and_cvar_dominates():
    r = _normal_returns()
    var = value_at_risk(r, level=0.95, method="historical")
    cvar = conditional_value_at_risk(r, level=0.95, method="historical")
    assert var > 0
    assert cvar >= var  # expected shortfall is never smaller than VaR


def test_historical_and_parametric_var_agree_for_normal():
    r = _normal_returns(seed=42, n=50_000)
    hist = value_at_risk(r, 0.95, "historical")
    param = value_at_risk(r, 0.95, "parametric")
    # For a (near-)normal sample the two methods should be close.
    assert abs(hist - param) < 0.002


def test_var_increases_with_confidence():
    r = _normal_returns()
    assert value_at_risk(r, 0.99) >= value_at_risk(r, 0.95)


def test_sharpe_sign_tracks_mean():
    rng = np.random.default_rng(0)
    positive = rng.normal(0.001, 0.005, 1000)
    negative = rng.normal(-0.001, 0.005, 1000)
    assert sharpe_ratio(positive) > 0
    assert sharpe_ratio(negative) < 0


def test_max_drawdown_is_non_positive():
    r = _normal_returns()
    assert max_drawdown(r) <= 0


def test_summary_has_all_keys():
    r = _normal_returns()
    keys = set(summary(r))
    assert {
        "annualized_return",
        "annualized_volatility",
        "sharpe_ratio",
        "sortino_ratio",
        "var_historical",
        "var_parametric",
        "cvar_historical",
        "max_drawdown",
    } <= keys
