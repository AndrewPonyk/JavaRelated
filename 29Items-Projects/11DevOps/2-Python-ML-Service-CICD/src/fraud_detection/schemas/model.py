"""Schemas for model registry, A/B configuration, drift and retraining."""

from __future__ import annotations

from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field


class ModelVersionInfo(BaseModel):
    """A registered model version."""

    name: str
    version: str
    stage: str = Field(description='Registry alias/stage: "champion", "challenger" or "archived".')
    auc: float | None = None
    registered_at: datetime | None = None
    source: str = Field(
        default="local", description='Registry tier the version lives in: "local"|"mlflow".'
    )


class ModelAliases(BaseModel):
    """Current alias -> version mapping."""

    champion: str | None = None
    challenger: str | None = None


class ModelListResponse(BaseModel):
    """All known model versions plus the alias mapping."""

    items: list[ModelVersionInfo]
    aliases: ModelAliases


class ABConfig(BaseModel):
    """Current champion/challenger traffic-split configuration."""

    model_config = ConfigDict(protected_namespaces=())

    enabled: bool
    traffic_split: int = Field(ge=0, le=100, description="Percent of traffic sent to challenger.")
    model_name: str
    champion_version: str | None = None
    challenger_version: str | None = None


class ABConfigUpdate(BaseModel):
    """Runtime update of the A/B experiment configuration."""

    enabled: bool | None = None
    traffic_split: int | None = Field(
        default=None, ge=0, le=100, description="Percent of traffic sent to challenger."
    )


class PromoteRequest(BaseModel):
    """Request to point a registry alias at a specific model version."""

    version: str = Field(min_length=1)
    alias: Literal["champion", "challenger"] = "champion"


class PromoteResponse(BaseModel):
    """Outcome of an alias promotion."""

    version: str
    alias: str
    previous: str | None = Field(
        default=None, description="Version the alias pointed at before the promotion."
    )


class DriftStatus(BaseModel):
    """Result of a PSI drift check for one feature."""

    feature_name: str
    psi_score: float
    threshold: float
    drift_detected: bool


class DriftSummary(BaseModel):
    """Aggregated drift evaluation over the recent prediction window."""

    status: Literal["ok", "drift_detected", "insufficient_data"]
    features: list[DriftStatus] = Field(default_factory=list)
    evaluated_rows: int = 0
    retraining_triggered: bool = False
    detail: str | None = None


class DriftReportRecord(BaseModel):
    """A persisted drift report row."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    feature_name: str
    psi_score: float
    threshold: float
    drift_detected: bool
    window_start: datetime
    window_end: datetime
    created_at: datetime


class DriftReportListResponse(BaseModel):
    """Recent drift reports (newest first)."""

    items: list[DriftReportRecord]


class RetrainRequest(BaseModel):
    """Manual retraining trigger."""

    reason: str = Field(default="manual", min_length=1)


class RetrainResponse(BaseModel):
    """Acknowledgement of an asynchronous retraining job submission."""

    job_id: str
    status: str
    reason: str
    submitted_at: datetime


class RetrainJobStatus(BaseModel):
    """Full status of a retraining job."""

    job_id: str
    status: Literal["queued", "running", "succeeded", "failed", "submitted"]
    reason: str
    submitted_at: datetime
    finished_at: datetime | None = None
    detail: str | None = None
    result: dict[str, Any] | None = Field(
        default=None, description="On success: new version and its evaluation metrics."
    )
