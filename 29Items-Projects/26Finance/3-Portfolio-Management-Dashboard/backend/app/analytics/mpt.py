"""Modern Portfolio Theory: mean-variance optimization and the efficient frontier.

Functions operate on **annualized** inputs:
    mu  : (N,)   vector of expected returns
    cov : (N, N) covariance matrix

Long-only (weights in [0, 1], summing to 1) is the default. Pass custom
``bounds`` (e.g. ``(-1, 1)``) to allow short positions. Constrained problems are
solved with SLSQP; an unconstrained closed-form path is exposed for testing.
"""

from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np
from scipy.optimize import minimize


@dataclass
class OptimizedPortfolio:
    """Result of an optimization: weights plus their realized risk/return."""

    weights: np.ndarray
    expected_return: float
    volatility: float
    sharpe: float
    labels: list[str] = field(default_factory=list)

    def as_dict(self) -> dict:
        return {
            "weights": {
                (self.labels[i] if i < len(self.labels) else str(i)): float(w)
                for i, w in enumerate(self.weights)
            },
            "expected_return": self.expected_return,
            "volatility": self.volatility,
            "sharpe": self.sharpe,
        }


# --------------------------------------------------------------------------- #
# Portfolio statistics
# --------------------------------------------------------------------------- #
def portfolio_return(weights: np.ndarray, mu: np.ndarray) -> float:
    return float(weights @ mu)


def portfolio_volatility(weights: np.ndarray, cov: np.ndarray) -> float:
    return float(np.sqrt(weights @ cov @ weights))


def portfolio_sharpe(
    weights: np.ndarray, mu: np.ndarray, cov: np.ndarray, risk_free_rate: float = 0.0
) -> float:
    vol = portfolio_volatility(weights, cov)
    if vol == 0:
        return 0.0
    return (portfolio_return(weights, mu) - risk_free_rate) / vol


def _make_portfolio(
    weights: np.ndarray,
    mu: np.ndarray,
    cov: np.ndarray,
    risk_free_rate: float,
    labels: list[str] | None,
) -> OptimizedPortfolio:
    return OptimizedPortfolio(
        weights=weights,
        expected_return=portfolio_return(weights, mu),
        volatility=portfolio_volatility(weights, cov),
        sharpe=portfolio_sharpe(weights, mu, cov, risk_free_rate),
        labels=labels or [],
    )


# --------------------------------------------------------------------------- #
# Optimizers
# --------------------------------------------------------------------------- #
def global_min_variance_closed_form(cov: np.ndarray) -> np.ndarray:
    """Unconstrained (shorts allowed) global minimum-variance weights.

    ``w = Σ⁻¹·𝟙 / (𝟙ᵀ·Σ⁻¹·𝟙)``. Used as an analytic oracle in tests.
    """
    ones = np.ones(cov.shape[0])
    inv = np.linalg.inv(cov)
    w = inv @ ones
    return w / (ones @ inv @ ones)


def minimum_variance_portfolio(
    mu: np.ndarray,
    cov: np.ndarray,
    risk_free_rate: float = 0.0,
    bounds: tuple[float, float] = (0.0, 1.0),
    labels: list[str] | None = None,
) -> OptimizedPortfolio:
    """Minimize portfolio variance subject to fully-invested weights."""
    n = len(mu)
    x0 = np.full(n, 1.0 / n)
    constraints = ({"type": "eq", "fun": lambda w: np.sum(w) - 1.0},)
    result = minimize(
        lambda w: w @ cov @ w,
        x0,
        method="SLSQP",
        bounds=[bounds] * n,
        constraints=constraints,
        options={"ftol": 1e-10, "maxiter": 500},
    )
    if not result.success:  # pragma: no cover - solver edge case
        raise RuntimeError(f"Min-variance optimization failed: {result.message}")
    return _make_portfolio(result.x, mu, cov, risk_free_rate, labels)


def maximum_sharpe_portfolio(
    mu: np.ndarray,
    cov: np.ndarray,
    risk_free_rate: float = 0.0,
    bounds: tuple[float, float] = (0.0, 1.0),
    labels: list[str] | None = None,
) -> OptimizedPortfolio:
    """Maximize the Sharpe ratio (the tangency portfolio)."""
    n = len(mu)
    x0 = np.full(n, 1.0 / n)
    constraints = ({"type": "eq", "fun": lambda w: np.sum(w) - 1.0},)

    def neg_sharpe(w: np.ndarray) -> float:
        vol = np.sqrt(w @ cov @ w)
        if vol == 0:
            return 0.0
        return -(w @ mu - risk_free_rate) / vol

    result = minimize(
        neg_sharpe,
        x0,
        method="SLSQP",
        bounds=[bounds] * n,
        constraints=constraints,
        options={"ftol": 1e-10, "maxiter": 500},
    )
    if not result.success:  # pragma: no cover - solver edge case
        raise RuntimeError(f"Max-Sharpe optimization failed: {result.message}")
    return _make_portfolio(result.x, mu, cov, risk_free_rate, labels)


def _min_variance_for_target(
    mu: np.ndarray,
    cov: np.ndarray,
    target_return: float,
    bounds: tuple[float, float],
) -> np.ndarray | None:
    n = len(mu)
    x0 = np.full(n, 1.0 / n)
    constraints = (
        {"type": "eq", "fun": lambda w: np.sum(w) - 1.0},
        {"type": "eq", "fun": lambda w: w @ mu - target_return},
    )
    result = minimize(
        lambda w: w @ cov @ w,
        x0,
        method="SLSQP",
        bounds=[bounds] * n,
        constraints=constraints,
        options={"ftol": 1e-10, "maxiter": 500},
    )
    return result.x if result.success else None


def efficient_frontier(
    mu: np.ndarray,
    cov: np.ndarray,
    n_points: int = 50,
    risk_free_rate: float = 0.0,
    bounds: tuple[float, float] = (0.0, 1.0),
    labels: list[str] | None = None,
) -> list[OptimizedPortfolio]:
    """Trace the efficient frontier as ``n_points`` minimum-variance portfolios.

    Targets are swept from the global minimum-variance portfolio's return up to
    the maximum attainable return (the highest-mu asset, under long-only).
    Infeasible targets are skipped rather than raising.
    """
    mu = np.asarray(mu, dtype=float)
    cov = np.asarray(cov, dtype=float)
    gmv = minimum_variance_portfolio(mu, cov, risk_free_rate, bounds, labels)
    lo, hi = gmv.expected_return, float(mu.max())
    if hi <= lo:
        return [gmv]

    frontier: list[OptimizedPortfolio] = []
    for target in np.linspace(lo, hi, n_points):
        weights = _min_variance_for_target(mu, cov, float(target), bounds)
        if weights is not None:
            frontier.append(_make_portfolio(weights, mu, cov, risk_free_rate, labels))
    return frontier
