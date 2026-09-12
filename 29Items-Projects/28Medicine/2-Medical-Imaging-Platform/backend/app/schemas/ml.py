"""ML prediction schemas."""

from __future__ import annotations

import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class MlPrediction(BaseModel):
    """One model's output for a single instance."""

    model_config = ConfigDict(from_attributes=True, protected_namespaces=())

    id: uuid.UUID
    model_name: str
    model_version: str
    predictions: dict[str, float]  # pathology -> probability
    top_label: str | None = None
    top_score: float | None = None
    heatmap_url: str | None = None
    created_at: datetime


class MlTriggerRequest(BaseModel):
    sop_instance_uid: str
    force: bool = Field(False, description="Re-run even if a result already exists")


class MlTriggerResponse(BaseModel):
    sop_instance_uid: str
    status: str  # "queued" | "skipped_unsupported_modality"
