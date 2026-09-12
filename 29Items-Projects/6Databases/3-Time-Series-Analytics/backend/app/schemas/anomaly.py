"""Anomaly detection result models."""

from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel


class Anomaly(BaseModel):
    device_id: str
    metric: str
    ts: datetime
    value: float
    expected: float | None = None  # forecast midpoint (Prophet yhat) / mean (z-score)
    lower: float | None = None  # forecast band lower bound
    upper: float | None = None  # forecast band upper bound
    score: float  # severity: band distance or |z|
    method: Literal["prophet", "zscore"]
