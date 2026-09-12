"""Pydantic contracts for model-registry and serving endpoints."""
from __future__ import annotations

from enum import Enum

from pydantic import BaseModel, Field


class ModelStage(str, Enum):
    NONE = "None"
    STAGING = "Staging"
    PRODUCTION = "Production"
    ARCHIVED = "Archived"


class ModelVersionOut(BaseModel):
    """A registered model version."""

    name: str
    version: int
    stage: ModelStage
    run_id: str
    framework: str


class StageTransitionRequest(BaseModel):
    """Promote/demote a model version (the only promotion mechanism)."""

    stage: ModelStage
    archive_existing: bool = True


class PredictionRequest(BaseModel):
    """Inference request against the unified serving layer."""

    features: dict[str, float | int | str | bool]
    subject_id: str | None = Field(
        default=None, description="Used for stable A/B bucketing."
    )


class PredictionResponse(BaseModel):
    """Inference result with provenance."""

    prediction: float | int | str | list[float]
    model_version: int
    variant: str
    confidence: float | None = None
