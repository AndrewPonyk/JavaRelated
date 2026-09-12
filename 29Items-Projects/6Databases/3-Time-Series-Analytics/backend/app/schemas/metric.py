"""Request/response models for metric ingest and queries."""

from __future__ import annotations

from datetime import UTC, datetime
from typing import Literal

from pydantic import BaseModel, Field, field_validator


class MetricPoint(BaseModel):
    device_id: str = Field(min_length=1, max_length=128)
    metric: str = Field(min_length=1, max_length=64, pattern=r"^[a-z][a-z0-9_.]*$")
    ts: datetime
    value: float
    tags: dict[str, str] = Field(default_factory=dict)

    @field_validator("ts")
    @classmethod
    def ensure_utc(cls, v: datetime) -> datetime:
        # Devices lie about time zones; naive timestamps are treated as UTC.
        # Age/skew policy is enforced in services.ingestion (needs settings).
        return v.replace(tzinfo=UTC) if v.tzinfo is None else v.astimezone(UTC)


class IngestBatch(BaseModel):
    points: list[MetricPoint] = Field(min_length=1)


class IngestResult(BaseModel):
    accepted: int
    rejected: int = 0  # too old / future-dated / unknown or disabled device


class SeriesPoint(BaseModel):
    ts: datetime
    value: float


class MetricSeries(BaseModel):
    device_id: str
    metric: str
    source: Literal["raw", "rollup_1h"]
    points: list[SeriesPoint]
    decimated: bool = False  # true when the response was stride-sampled


class LiveAggregate(BaseModel):
    """Current 1-minute window stats served straight from Redis."""

    device_id: str
    metric: str
    window_start: datetime
    count: int
    sum: float
    avg: float | None = None
    min: float | None = None
    max: float | None = None
    latest: float | None = None
