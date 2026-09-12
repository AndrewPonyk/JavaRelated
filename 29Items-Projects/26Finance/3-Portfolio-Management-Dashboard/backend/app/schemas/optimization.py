from __future__ import annotations

from enum import Enum

from pydantic import BaseModel, Field


class Objective(str, Enum):
    MIN_VARIANCE = "min_variance"
    MAX_SHARPE = "max_sharpe"


class OptimizationRequest(BaseModel):
    objective: Objective = Objective.MAX_SHARPE
    allow_short: bool = False
    risk_free_rate: float | None = Field(default=None, description="Annual; overrides default")
    lookback_days: int = Field(default=252, ge=30, le=2520)
    covariance_shrinkage: float = Field(default=0.0, ge=0.0, le=1.0)
    # Constraint-aware optimization (long-only): per-asset weight caps/floors.
    min_weight: float = Field(default=0.0, ge=0.0, lt=1.0)
    max_weight: float | None = Field(default=None, gt=0.0, le=1.0)


class OptimizedPortfolioRead(BaseModel):
    objective: Objective
    weights: dict[str, float]
    expected_return: float
    volatility: float
    sharpe: float


class FrontierRequest(BaseModel):
    n_points: int = Field(default=50, ge=5, le=200)
    allow_short: bool = False
    lookback_days: int = Field(default=252, ge=30, le=2520)
    covariance_shrinkage: float = Field(default=0.0, ge=0.0, le=1.0)
    min_weight: float = Field(default=0.0, ge=0.0, lt=1.0)
    max_weight: float | None = Field(default=None, gt=0.0, le=1.0)


class FrontierPoint(BaseModel):
    expected_return: float
    volatility: float
    sharpe: float
    weights: dict[str, float]


class FrontierResponse(BaseModel):
    points: list[FrontierPoint]
    max_sharpe: OptimizedPortfolioRead
    min_variance: OptimizedPortfolioRead
