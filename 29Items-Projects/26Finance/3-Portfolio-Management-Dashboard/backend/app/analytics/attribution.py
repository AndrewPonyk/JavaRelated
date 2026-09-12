"""Performance & risk attribution.

Decomposes a portfolio's return and volatility into per-asset contributions:

* **Return contribution** (buy-and-hold, initial weights):
  ``contribution_i = w_i * cumulative_return_i``; these sum to the portfolio
  return (ignoring intra-period rebalancing/compounding cross-terms).
* **Risk contribution** (Euler decomposition of volatility):
  marginal contribution ``MCTR_i = (Σw)_i / σ_p`` and component contribution
  ``CCTR_i = w_i * MCTR_i``; the CCTRs sum exactly to ``σ_p``.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np


@dataclass
class AssetAttribution:
    symbol: str
    weight: float
    asset_return: float
    return_contribution: float
    pct_of_return: float
    risk_contribution: float
    pct_of_risk: float


@dataclass
class AttributionResult:
    portfolio_return: float
    portfolio_volatility: float
    assets: list[AssetAttribution]

    def as_dict(self) -> dict:
        return {
            "portfolio_return": self.portfolio_return,
            "portfolio_volatility": self.portfolio_volatility,
            "assets": [a.__dict__ for a in self.assets],
        }


def performance_attribution(
    symbols: list[str],
    weights: np.ndarray,
    asset_cumulative_returns: np.ndarray,
    cov: np.ndarray,
) -> AttributionResult:
    """Compute per-asset return and risk contributions.

    Args:
        symbols: Asset labels, length N.
        weights: Portfolio weights, length N.
        asset_cumulative_returns: Each asset's total return over the window, len N.
        cov: Annualized covariance matrix, (N, N).
    """
    weights = np.asarray(weights, dtype=float)
    asset_cumulative_returns = np.asarray(asset_cumulative_returns, dtype=float)
    cov = np.asarray(cov, dtype=float)

    return_contrib = weights * asset_cumulative_returns
    portfolio_return = float(return_contrib.sum())

    portfolio_vol = float(np.sqrt(weights @ cov @ weights))
    if portfolio_vol > 0:
        mctr = (cov @ weights) / portfolio_vol
        cctr = weights * mctr  # sums to portfolio_vol
    else:
        cctr = np.zeros_like(weights)

    def _pct(value: float, total: float) -> float:
        return float(value / total) if total else 0.0

    assets = [
        AssetAttribution(
            symbol=symbols[i],
            weight=float(weights[i]),
            asset_return=float(asset_cumulative_returns[i]),
            return_contribution=float(return_contrib[i]),
            pct_of_return=_pct(float(return_contrib[i]), portfolio_return),
            risk_contribution=float(cctr[i]),
            pct_of_risk=_pct(float(cctr[i]), portfolio_vol),
        )
        for i in range(len(symbols))
    ]
    return AttributionResult(portfolio_return, portfolio_vol, assets)
