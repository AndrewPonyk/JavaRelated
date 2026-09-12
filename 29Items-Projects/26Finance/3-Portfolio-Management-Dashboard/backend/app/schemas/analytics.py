from __future__ import annotations

from pydantic import BaseModel, Field


class AssetAttribution(BaseModel):
    symbol: str
    weight: float
    asset_return: float
    return_contribution: float
    pct_of_return: float
    risk_contribution: float
    pct_of_risk: float


class AttributionResponse(BaseModel):
    portfolio_return: float
    portfolio_volatility: float
    assets: list[AssetAttribution]


class BacktestRequest(BaseModel):
    lookback: int = Field(default=126, ge=20, le=1260)
    rebalance_every: int = Field(default=21, ge=1, le=252)
    objective: str = Field(default="max_sharpe", pattern="^(max_sharpe|min_variance)$")
    allow_short: bool = False
    lookback_days: int = Field(default=756, ge=60, le=2520)


class BacktestResponse(BaseModel):
    dates: list[str]
    strategy_equity: list[float]
    benchmark_equity: list[float]
    strategy_cagr: float
    strategy_volatility: float
    strategy_sharpe: float
    strategy_max_drawdown: float
    benchmark_cagr: float
    n_rebalances: int
