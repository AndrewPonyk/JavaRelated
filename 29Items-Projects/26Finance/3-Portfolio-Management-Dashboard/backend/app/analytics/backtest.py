"""Walk-forward backtest with periodic rebalancing.

At each rebalance date the optimizer re-estimates mu/Sigma from a *trailing*
window (no look-ahead) and sets new target weights, which are then held until
the next rebalance. The resulting strategy equity curve is compared against an
equal-weight buy-and-hold benchmark.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pandas as pd

from .mpt import maximum_sharpe_portfolio, minimum_variance_portfolio
from .returns import TRADING_DAYS_PER_YEAR, to_returns
from .risk_metrics import annualized_return, annualized_volatility, max_drawdown, sharpe_ratio


@dataclass
class BacktestResult:
    dates: list[str]
    strategy_equity: list[float]
    benchmark_equity: list[float]
    strategy_cagr: float
    strategy_volatility: float
    strategy_sharpe: float
    strategy_max_drawdown: float
    benchmark_cagr: float
    n_rebalances: int

    def as_dict(self) -> dict:
        return self.__dict__


def backtest_rebalanced(
    prices: pd.DataFrame,
    lookback: int = 126,
    rebalance_every: int = 21,
    objective: str = "max_sharpe",
    risk_free_rate: float = 0.0,
    allow_short: bool = False,
    periods_per_year: int = TRADING_DAYS_PER_YEAR,
) -> BacktestResult:
    returns = to_returns(prices, method="simple")
    if len(returns) <= lookback:
        raise ValueError("Not enough history for the requested lookback window")

    matrix = returns.to_numpy()
    n_periods, n_assets = matrix.shape
    bounds = (-1.0, 1.0) if allow_short else (0.0, 1.0)
    equal_weight = np.full(n_assets, 1.0 / n_assets)

    weights = equal_weight
    strat_returns: list[float] = []
    bench_returns: list[float] = []
    n_rebalances = 0

    for t in range(lookback, n_periods):
        if (t - lookback) % rebalance_every == 0:
            window = returns.iloc[t - lookback : t]
            mu = window.mean().to_numpy() * periods_per_year
            cov = window.cov().to_numpy() * periods_per_year
            try:
                if objective == "min_variance":
                    weights = minimum_variance_portfolio(mu, cov, risk_free_rate, bounds).weights
                else:
                    weights = maximum_sharpe_portfolio(mu, cov, risk_free_rate, bounds).weights
                n_rebalances += 1
            except Exception:  # noqa: BLE001 - keep prior weights on solver failure
                pass

        strat_returns.append(float(matrix[t] @ weights))
        bench_returns.append(float(matrix[t] @ equal_weight))

    strat = np.array(strat_returns)
    bench = np.array(bench_returns)
    strat_equity = np.cumprod(1.0 + strat)
    bench_equity = np.cumprod(1.0 + bench)
    dates = [d.strftime("%Y-%m-%d") for d in returns.index[lookback:]]

    return BacktestResult(
        dates=dates,
        strategy_equity=strat_equity.tolist(),
        benchmark_equity=bench_equity.tolist(),
        strategy_cagr=annualized_return(strat, periods_per_year),
        strategy_volatility=annualized_volatility(strat, periods_per_year),
        strategy_sharpe=sharpe_ratio(strat, risk_free_rate, periods_per_year),
        strategy_max_drawdown=max_drawdown(strat),
        benchmark_cagr=annualized_return(bench, periods_per_year),
        n_rebalances=n_rebalances,
    )
