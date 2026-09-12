from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, Field


class CurrentMetric(BaseModel):
    """Latest closed window for one metric, as stored in the Redis hot store."""

    metric: str = Field(examples=["orders_per_second"])
    value: float
    window_start_ms: int = Field(ge=0, description="Event-time window start (epoch ms)")
    window_ms: int = Field(gt=0, description="Window width in ms")


class MetricHistoryPoint(BaseModel):
    """One point from fct_business_metrics_daily (warehouse system of record)."""

    metric: str
    bucket_start: datetime
    value: float
