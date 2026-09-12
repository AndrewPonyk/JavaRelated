"""Data contracts for every Kafka topic and OpenSearch index.

These models ARE the interface between producers, Spark jobs, the alerting engine and the
API. Schema changes happen here first, with tests, and must stay backward compatible
(consumers tolerate unknown fields; new fields are optional).
"""

from __future__ import annotations

import hashlib
from datetime import datetime, timezone
from enum import Enum
from typing import Any
from uuid import uuid4

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from log_analytics.common.parsing import normalize_level, parse_timestamp


class LogLevel(str, Enum):
    TRACE = "TRACE"
    DEBUG = "DEBUG"
    INFO = "INFO"
    WARN = "WARN"
    ERROR = "ERROR"
    FATAL = "FATAL"


class LogEvent(BaseModel):
    """Canonical log event — the payload of `logs.raw` / `logs.enriched` and `la-logs` docs."""

    model_config = ConfigDict(extra="ignore")

    timestamp: datetime
    service: str = Field(min_length=1, max_length=128)
    env: str = "dev"
    level: LogLevel = LogLevel.INFO
    message: str = Field(max_length=32_768)
    host: str | None = None
    trace_id: str | None = None
    span_id: str | None = None
    # Arbitrary producer-specific structure; indexed as flat_object (never dynamic mappings).
    attributes: dict[str, Any] = Field(default_factory=dict)

    @field_validator("timestamp", mode="before")
    @classmethod
    def _coerce_timestamp(cls, value: Any) -> Any:
        parsed = parse_timestamp(value)
        if parsed is None:
            raise ValueError(f"unparseable timestamp: {value!r}")
        return parsed

    @field_validator("level", mode="before")
    @classmethod
    def _coerce_level(cls, value: Any) -> Any:
        if isinstance(value, str):
            return normalize_level(value)
        return value

    def doc_id(self) -> str:
        """Deterministic OpenSearch _id → idempotent indexing (effectively-once)."""
        raw = f"{self.service}|{self.timestamp.isoformat()}|{self.host}|{self.message}"
        return hashlib.sha1(raw.encode("utf-8")).hexdigest()


class AnomalyRecord(BaseModel):
    """Payload of `logs.anomalies` and `la-anomalies` docs (one per service per window)."""

    model_config = ConfigDict(extra="ignore")

    service: str
    window_start: datetime
    window_end: datetime
    score: float = Field(ge=0.0, le=1.0, description="normalized anomaly score, 1 = most anomalous")
    is_anomaly: bool
    features: dict[str, float] = Field(default_factory=dict)
    model_version: str = "unknown"


class AlertSeverity(str, Enum):
    INFO = "info"
    WARNING = "warning"
    CRITICAL = "critical"


class AlertEvent(BaseModel):
    """Payload of `alerts.events` and `la-alerts` docs."""

    model_config = ConfigDict(extra="ignore")

    id: str = Field(default_factory=lambda: str(uuid4()))
    rule_id: str
    severity: AlertSeverity = AlertSeverity.WARNING
    title: str
    description: str = ""
    source: str = "pattern"  # pattern | anomaly
    service: str = ""
    dedup_key: str = ""
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    context: dict[str, Any] = Field(default_factory=dict)

    @model_validator(mode="after")
    def _default_dedup_key(self) -> AlertEvent:
        # model_validator (not field_validator): defaults skip field validation in pydantic v2.
        if not self.dedup_key:
            self.dedup_key = f"{self.rule_id}|{self.service}"
        return self
