"""Pure quant core: return estimation, MPT optimization, risk metrics, Monte Carlo.

This package has **no** I/O, framework, or database dependencies. It takes NumPy/
Pandas in and returns numbers out, which keeps the most valuable code in the
product trivially unit-testable and reusable from APIs, workers, and notebooks.
"""

from .attribution import AttributionResult, performance_attribution
from .backtest import BacktestResult, backtest_rebalanced
from .monte_carlo import SimulationResult, simulate_portfolio
from .mpt import (
    OptimizedPortfolio,
    efficient_frontier,
    maximum_sharpe_portfolio,
    minimum_variance_portfolio,
    portfolio_return,
    portfolio_volatility,
)
from .returns import (
    TRADING_DAYS_PER_YEAR,
    annualized_covariance,
    annualized_mean,
    estimate_mu_sigma,
    portfolio_returns,
    to_returns,
)
from .risk_metrics import (
    conditional_value_at_risk,
    max_drawdown,
    sharpe_ratio,
    sortino_ratio,
    summary,
    value_at_risk,
)

__all__ = [
    "TRADING_DAYS_PER_YEAR",
    "AttributionResult",
    "BacktestResult",
    "OptimizedPortfolio",
    "SimulationResult",
    "annualized_covariance",
    "annualized_mean",
    "backtest_rebalanced",
    "conditional_value_at_risk",
    "efficient_frontier",
    "estimate_mu_sigma",
    "max_drawdown",
    "maximum_sharpe_portfolio",
    "minimum_variance_portfolio",
    "performance_attribution",
    "portfolio_return",
    "portfolio_returns",
    "portfolio_volatility",
    "sharpe_ratio",
    "simulate_portfolio",
    "sortino_ratio",
    "summary",
    "to_returns",
    "value_at_risk",
]
