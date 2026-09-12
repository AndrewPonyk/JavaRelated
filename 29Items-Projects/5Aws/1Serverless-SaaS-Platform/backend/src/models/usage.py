import re
from typing import Any

from pydantic import BaseModel, Field, field_validator


class UsageEventIngestRequest(BaseModel):
    metric: str = Field(..., min_length=2, max_length=50, description="Metric identifier, e.g. 'api_calls'")
    count: int = Field(default=1, ge=1, le=1_000_000, description="Quantity to increment")
    idempotency_key: str = Field(..., min_length=4, max_length=128, description="Unique client deduplication ID")
    timestamp: str | None = Field(default=None, description="ISO-8601 timestamp of client event")
    metadata: dict[str, Any] = Field(default_factory=dict, description="Contextual event metadata")

    @field_validator("metric")
    @classmethod
    def sanitize_metric(cls, v: str) -> str:
        cleaned = v.strip().lower()
        if not re.match(r"^[a-z0-9_.-]+$", cleaned):
            raise ValueError("Metric identifier must only contain lowercase alphanumeric characters, dots, and underscores.")
        return cleaned

    @field_validator("idempotency_key")
    @classmethod
    def sanitize_idempotency(cls, v: str) -> str:
        cleaned = v.strip()
        if len(cleaned) < 4:
            raise ValueError("Idempotency key must be at least 4 non-whitespace characters.")
        return cleaned


class UsageBatchIngestRequest(BaseModel):
    events: list[UsageEventIngestRequest] = Field(..., min_length=1, max_length=100)


class UsageMetricsResponse(BaseModel):
    tenant_id: str
    metric: str
    period: str
    total_count: int
    quota_limit: int
    percent_consumed: float
    warning_threshold_exceeded: bool
    last_updated: str


class UsageHistoryPoint(BaseModel):
    date: str
    count: int
    metric: str


class UsageHistoryResponse(BaseModel):
    tenant_id: str
    metric: str
    points: list[UsageHistoryPoint]
    total_points: int
