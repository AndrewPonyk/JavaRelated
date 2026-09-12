"""Risk and performance metrics for a portfolio return series.

Every function takes a 1-D array-like of *periodic* (e.g. daily) returns and,
where relevant, annualizes using ``periods_per_year``. Value-at-Risk and CVaR
are returned as **positive loss magnitudes** (e.g. ``0.031`` == a 3.1% loss),
which is the convention the API and UI expect.
"""

from __future__ import annotations

import numpy as np
from scipy import stats

from .returns import TRADING_DAYS_PER_YEAR


def _as_array(returns) -> np.ndarray:
    arr = np.asarray(returns, dtype=float).ravel()
    if arr.size == 0:
        raise ValueError("returns series is empty")
    return arr


def annualized_return(returns, periods_per_year: int = TRADING_DAYS_PER_YEAR) -> float:
    """Geometric (compounded) annualized return."""
    arr = _as_array(returns)
    growth = float(np.prod(1.0 + arr))
    years = arr.size / periods_per_year
    if years <= 0:
        return 0.0
    return growth ** (1.0 / years) - 1.0


def annualized_volatility(returns, periods_per_year: int = TRADING_DAYS_PER_YEAR) -> float:
    """Annualized standard deviation of returns."""
    arr = _as_array(returns)
    return float(np.std(arr, ddof=1) * np.sqrt(periods_per_year))


def sharpe_ratio(
    returns,
    risk_free_rate: float = 0.0,
    periods_per_year: int = TRADING_DAYS_PER_YEAR,
) -> float:
    """Annualized Sharpe ratio. ``risk_free_rate`` is an annual rate."""
    arr = _as_array(returns)
    rf_per_period = risk_free_rate / periods_per_year
    excess = arr - rf_per_period
    sd = np.std(excess, ddof=1)
    if sd == 0:
        return 0.0
    return float(np.mean(excess) / sd * np.sqrt(periods_per_year))


def sortino_ratio(
    returns,
    risk_free_rate: float = 0.0,
    periods_per_year: int = TRADING_DAYS_PER_YEAR,
) -> float:
    """Annualized Sortino ratio (penalizes only downside deviation)."""
    arr = _as_array(returns)
    rf_per_period = risk_free_rate / periods_per_year
    excess = arr - rf_per_period
    downside = excess[excess < 0]
    if downside.size == 0:
        return float("inf")
    downside_dev = np.sqrt(np.mean(downside**2))
    if downside_dev == 0:
        return 0.0
    return float(np.mean(excess) / downside_dev * np.sqrt(periods_per_year))


def value_at_risk(returns, level: float = 0.95, method: str = "historical") -> float:
    """Value-at-Risk at the given confidence ``level`` (single-period).

    Returns a positive loss fraction. ``method`` is ``"historical"`` (empirical
    quantile) or ``"parametric"`` (Gaussian).
    """
    arr = _as_array(returns)
    alpha = 1.0 - level
    if method == "historical":
        quantile = np.percentile(arr, alpha * 100.0)
    elif method == "parametric":
        mu, sigma = np.mean(arr), np.std(arr, ddof=1)
        quantile = mu + sigma * stats.norm.ppf(alpha)
    else:  # pragma: no cover - guard
        raise ValueError(f"Unknown VaR method: {method!r}")
    return float(-quantile)


def conditional_value_at_risk(returns, level: float = 0.95, method: str = "historical") -> float:
    """Conditional VaR / Expected Shortfall: mean loss beyond the VaR threshold.

    Returns a positive loss fraction; always >= the corresponding VaR.
    """
    arr = _as_array(returns)
    alpha = 1.0 - level
    if method == "historical":
        quantile = np.percentile(arr, alpha * 100.0)
        tail = arr[arr <= quantile]
        if tail.size == 0:
            return float(-quantile)
        return float(-tail.mean())
    elif method == "parametric":
        mu, sigma = np.mean(arr), np.std(arr, ddof=1)
        z = stats.norm.ppf(alpha)
        es = -mu + sigma * stats.norm.pdf(z) / alpha
        return float(es)
    else:  # pragma: no cover - guard
        raise ValueError(f"Unknown CVaR method: {method!r}")


def max_drawdown(returns) -> float:
    """Maximum peak-to-trough drawdown of the equity curve (a negative number)."""
    arr = _as_array(returns)
    wealth = np.cumprod(1.0 + arr)
    running_peak = np.maximum.accumulate(wealth)
    drawdown = wealth / running_peak - 1.0
    return float(drawdown.min())


def beta(asset_returns, market_returns) -> float:
    """CAPM beta of an asset vs the market (cov / market variance)."""
    a = _as_array(asset_returns)
    m = _as_array(market_returns)
    if a.size != m.size:
        raise ValueError("asset and market series must be the same length")
    var_m = np.var(m, ddof=1)
    if var_m == 0:
        return 0.0
    return float(np.cov(a, m, ddof=1)[0, 1] / var_m)


def summary(
    returns,
    risk_free_rate: float = 0.0,
    var_level: float = 0.95,
    periods_per_year: int = TRADING_DAYS_PER_YEAR,
) -> dict[str, float]:
    """One-shot dashboard payload: every headline metric for a return series."""
    return {
        "annualized_return": annualized_return(returns, periods_per_year),
        "annualized_volatility": annualized_volatility(returns, periods_per_year),
        "sharpe_ratio": sharpe_ratio(returns, risk_free_rate, periods_per_year),
        "sortino_ratio": sortino_ratio(returns, risk_free_rate, periods_per_year),
        "var_historical": value_at_risk(returns, var_level, "historical"),
        "var_parametric": value_at_risk(returns, var_level, "parametric"),
        "cvar_historical": conditional_value_at_risk(returns, var_level, "historical"),
        "max_drawdown": max_drawdown(returns),
    }
