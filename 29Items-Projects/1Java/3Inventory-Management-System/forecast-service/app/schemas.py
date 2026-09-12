import math
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field, field_validator


class ForecastRequest(BaseModel):
    sku: str = Field(min_length=1, max_length=64)
    demand_history: list[float] = Field(min_length=1, max_length=730)
    horizon_days: int = Field(default=7, ge=1, le=90)
    feature_version: str = Field(default="daily-demand-v1", min_length=1, max_length=80)

    @field_validator("demand_history")
    @classmethod
    def demand_must_be_finite_and_non_negative(cls, values: list[float]) -> list[float]:
        if any(not math.isfinite(value) or value < 0 for value in values):
            raise ValueError("demand values must be finite and non-negative")
        return values


class ForecastResponse(BaseModel):
    sku: str
    predictions: list[float]
    lower_bound: list[float]
    upper_bound: list[float]
    model_version: str
    feature_version: str
    model_mae: float
    generated_at: datetime


class TrainModelRequest(BaseModel):
    demand_history: list[float] = Field(min_length=14, max_length=5000)
    feature_version: str = Field(default="daily-demand-v1", min_length=1, max_length=80)
    model_version: str | None = Field(default=None, max_length=120)

    @field_validator("demand_history")
    @classmethod
    def training_demand_must_be_valid(cls, values: list[float]) -> list[float]:
        if any(not math.isfinite(value) or value < 0 for value in values):
            raise ValueError("demand values must be finite and non-negative")
        return values


class ModelResponse(BaseModel):
    version: str
    feature_version: str
    alpha: float
    beta: float
    mae: float
    residual_stddev: float
    trained_at: datetime
    checksum: str


class HealthResponse(BaseModel):
    status: Literal["ok", "degraded"]
    redis: Literal["up", "down", "not_checked"]
    model_version: str
    feature_version: str
