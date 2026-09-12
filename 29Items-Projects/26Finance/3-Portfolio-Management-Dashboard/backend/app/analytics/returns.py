"""Return and covariance estimation.

Pure NumPy/Pandas helpers that turn a price history into the inputs the
optimizer and risk engine consume: a vector of (annualized) expected returns
``mu`` and an (annualized) covariance matrix ``Sigma``.

All annualization conventions live in this module so the rest of the codebase
never annualizes twice — a classic source of silent Sharpe/VaR corruption.
"""

from __future__ import annotations

import numpy as np
import pandas as pd

TRADING_DAYS_PER_YEAR = 252


def to_returns(prices: pd.DataFrame, method: str = "log") -> pd.DataFrame:
    """Convert a (T x N) price frame into a period-over-period return frame.

    Args:
        prices: DataFrame indexed by date, one column per asset.
        method: ``"log"`` for log returns (time-additive) or ``"simple"``.

    Returns:
        Return frame with the first (NaN) row dropped.
    """
    if prices.isna().any().any():
        prices = prices.ffill().dropna(how="any")
    if method == "log":
        rets = np.log(prices / prices.shift(1))
    elif method == "simple":
        rets = prices.pct_change()
    else:  # pragma: no cover - guard
        raise ValueError(f"Unknown return method: {method!r}")
    return rets.dropna(how="any")


def annualized_mean(
    returns: pd.DataFrame, periods_per_year: int = TRADING_DAYS_PER_YEAR
) -> pd.Series:
    """Annualized expected return per asset (arithmetic mean x periods)."""
    return returns.mean() * periods_per_year


def annualized_covariance(
    returns: pd.DataFrame, periods_per_year: int = TRADING_DAYS_PER_YEAR
) -> pd.DataFrame:
    """Annualized covariance matrix (sample covariance x periods)."""
    return returns.cov() * periods_per_year


def shrunk_covariance(cov: pd.DataFrame, delta: float = 0.1) -> pd.DataFrame:
    """Shrink a sample covariance toward a scaled-identity target.

    A lightweight stand-in for Ledoit-Wolf shrinkage. Pulling the matrix toward
    a diagonal target keeps it well-conditioned / positive-definite, which
    stabilizes the optimizer when asset histories are short or collinear.

    Args:
        cov: Sample covariance matrix.
        delta: Shrinkage intensity in [0, 1]; 0 = raw sample, 1 = pure target.
    """
    if not 0.0 <= delta <= 1.0:
        raise ValueError("delta must be in [0, 1]")
    values = cov.to_numpy()
    target = np.eye(values.shape[0]) * np.trace(values) / values.shape[0]
    shrunk = (1.0 - delta) * values + delta * target
    return pd.DataFrame(shrunk, index=cov.index, columns=cov.columns)


def estimate_mu_sigma(
    prices: pd.DataFrame,
    method: str = "log",
    periods_per_year: int = TRADING_DAYS_PER_YEAR,
    shrink: float = 0.0,
) -> tuple[np.ndarray, np.ndarray, list[str]]:
    """Convenience bridge: prices -> (mu, Sigma, asset_labels) as NumPy arrays.

    This is the single entry point the service layer uses to feed the optimizer
    and Monte Carlo engine, guaranteeing a consistent annualization basis.
    """
    returns = to_returns(prices, method=method)
    mu = annualized_mean(returns, periods_per_year)
    cov = annualized_covariance(returns, periods_per_year)
    if shrink > 0.0:
        cov = shrunk_covariance(cov, delta=shrink)
    labels = list(prices.columns)
    return mu.to_numpy(), cov.to_numpy(), labels


def portfolio_returns(returns: pd.DataFrame, weights: np.ndarray) -> pd.Series:
    """Collapse an asset return frame into a single portfolio return series.

    Assumes constant target weights (daily rebalancing). Used to feed the
    risk-metric functions in :mod:`risk_metrics`.
    """
    weights = np.asarray(weights, dtype=float)
    if weights.shape[0] != returns.shape[1]:
        raise ValueError("weights length must match number of assets")
    series = returns.to_numpy() @ weights
    return pd.Series(series, index=returns.index, name="portfolio")
