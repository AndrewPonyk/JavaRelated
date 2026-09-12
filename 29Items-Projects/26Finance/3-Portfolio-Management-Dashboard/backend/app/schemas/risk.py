from __future__ import annotations

from pydantic import BaseModel, Field


class RiskMetricsResponse(BaseModel):
    annualized_return: float
    annualized_volatility: float
    sharpe_ratio: float
    sortino_ratio: float
    var_historical: float
    var_parametric: float
    cvar_historical: float
    max_drawdown: float


class MonteCarloRequest(BaseModel):
    n_days: int = Field(default=252, ge=1, le=2520)
    n_sims: int = Field(default=10_000, ge=100, le=200_000)
    initial_value: float = Field(default=10_000.0, gt=0)
    var_level: float = Field(default=0.95, gt=0.5, lt=1.0)
    seed: int | None = None


class MonteCarloBands(BaseModel):
    t: list[int]
    p5: list[float]
    p50: list[float]
    p95: list[float]


class MonteCarloResponse(BaseModel):
    horizon_days: int
    n_sims: int
    initial_value: float
    expected_terminal_value: float
    median_terminal_value: float
    prob_loss: float
    var: float
    cvar: float
    var_level: float
    bands: MonteCarloBands
