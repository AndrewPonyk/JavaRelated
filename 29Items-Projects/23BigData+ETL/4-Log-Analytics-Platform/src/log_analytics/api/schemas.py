"""Request/response models for the Query & Admin API — validation lives here, not in routers."""

from __future__ import annotations

import re
from datetime import datetime
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator

from log_analytics.common.models import AlertSeverity

_WINDOW_RE = re.compile(r"^\d+[smh]$")  # e.g. 30s, 5m, 1h

# Metrics produced by the pattern-detection job — the vocabulary rules may reference.
SUPPORTED_METRICS = frozenset(
    {"log_count", "error_count", "error_ratio", "warn_ratio", "anomaly_score"}
)
SUPPORTED_OPS = frozenset({"gt", "gte", "lt", "lte"})
SUPPORTED_CHANNELS = frozenset({"log", "slack", "pagerduty"})


# Shared validation logic (used by both Create and partial Update models).


def _validate_metric(v: str) -> str:
    if v not in SUPPORTED_METRICS:
        raise ValueError(f"unsupported metric {v!r}; expected one of {sorted(SUPPORTED_METRICS)}")
    return v


def _validate_op(v: str) -> str:
    if v not in SUPPORTED_OPS:
        raise ValueError(f"unsupported operator {v!r}; expected one of {sorted(SUPPORTED_OPS)}")
    return v


def _validate_window(v: str) -> str:
    if not _WINDOW_RE.match(v):
        raise ValueError("window must look like 30s, 5m or 1h")
    return v


def _validate_channels(v: list[str]) -> list[str]:
    unknown = set(v) - SUPPORTED_CHANNELS
    if unknown:
        raise ValueError(
            f"unknown channels {sorted(unknown)}; expected {sorted(SUPPORTED_CHANNELS)}"
        )
    if not v:
        raise ValueError("at least one channel required")
    return v


class AlertRuleBase(BaseModel):
    name: str = Field(min_length=3, max_length=120)
    description: str = Field(default="", max_length=1000)
    metric: str
    op: str = "gt"
    threshold: float = Field(ge=0)
    window: str = Field(default="5m", description="evaluation window, e.g. 30s / 5m / 1h")
    severity: AlertSeverity = AlertSeverity.WARNING
    channels: list[str] = Field(default_factory=lambda: ["log"])
    enabled: bool = True

    @field_validator("metric")
    @classmethod
    def _check_metric(cls, v: str) -> str:
        return _validate_metric(v)

    @field_validator("op")
    @classmethod
    def _check_op(cls, v: str) -> str:
        return _validate_op(v)

    @field_validator("window")
    @classmethod
    def _check_window(cls, v: str) -> str:
        return _validate_window(v)

    @field_validator("channels")
    @classmethod
    def _check_channels(cls, v: list[str]) -> list[str]:
        return _validate_channels(v)


class AlertRuleCreate(AlertRuleBase):
    pass


class AlertRuleUpdate(BaseModel):
    """Partial update — only provided fields change."""

    name: str | None = Field(default=None, min_length=3, max_length=120)
    description: str | None = None
    metric: str | None = None
    op: str | None = None
    threshold: float | None = Field(default=None, ge=0)
    window: str | None = None
    severity: AlertSeverity | None = None
    channels: list[str] | None = None
    enabled: bool | None = None

    @field_validator("metric")
    @classmethod
    def _check_metric(cls, v: str | None) -> str | None:
        return v if v is None else _validate_metric(v)

    @field_validator("op")
    @classmethod
    def _check_op(cls, v: str | None) -> str | None:
        return v if v is None else _validate_op(v)

    @field_validator("window")
    @classmethod
    def _check_window(cls, v: str | None) -> str | None:
        return v if v is None else _validate_window(v)

    @field_validator("channels")
    @classmethod
    def _check_channels(cls, v: list[str] | None) -> list[str] | None:
        return v if v is None else _validate_channels(v)


class AlertRuleRead(AlertRuleBase):
    model_config = ConfigDict(from_attributes=True)

    id: str
    created_at: datetime
    updated_at: datetime


class AlertRuleList(BaseModel):
    items: list[AlertRuleRead]
    total: int


class LogSearchResponse(BaseModel):
    total: int
    took_ms: int
    hits: list[dict[str, Any]]
