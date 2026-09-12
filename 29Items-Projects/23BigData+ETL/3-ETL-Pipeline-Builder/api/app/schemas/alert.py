from __future__ import annotations

from datetime import datetime
from enum import Enum

from pydantic import BaseModel, Field


class AlertSeverity(str, Enum):
    info = "info"
    warning = "warning"
    critical = "critical"


class Alert(BaseModel):
    """Anomaly alert as published by the stream processor."""

    alert_id: str
    metric: str
    value: float
    score: float = Field(description="z-score against the online baseline")
    severity: AlertSeverity
    message: str
    triggered_at: datetime
    acknowledged: bool = False
