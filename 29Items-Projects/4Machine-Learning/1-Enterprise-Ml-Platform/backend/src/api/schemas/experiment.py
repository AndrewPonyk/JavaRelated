"""Pydantic contracts for experiment endpoints.

These define the API boundary: input validation + response shaping. They are
intentionally decoupled from ORM models so internal storage can evolve freely.
"""
from __future__ import annotations

from datetime import datetime
from enum import Enum

from pydantic import BaseModel, Field, field_validator


class RunStatus(str, Enum):
    PENDING = "PENDING"
    RUNNING = "RUNNING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"


class ExperimentCreate(BaseModel):
    """Request body to create an experiment."""

    name: str = Field(min_length=3, max_length=128)
    description: str | None = Field(default=None, max_length=1024)
    tags: dict[str, str] = Field(default_factory=dict)


class ExperimentOut(BaseModel):
    """Experiment returned to clients."""

    id: str
    name: str
    description: str | None = None
    tags: dict[str, str] = Field(default_factory=dict)
    created_at: datetime


_ALLOWED_FRAMEWORKS = {"pytorch", "tensorflow", "sklearn", "xgboost"}


class TrainingRequest(BaseModel):
    """Kick off a training run within an experiment."""

    framework: str = Field(examples=["pytorch", "tensorflow", "sklearn", "xgboost"])
    dataset_uri: str = Field(min_length=1, examples=["s3://datasets/churn/v3"])
    model_name: str | None = Field(
        default=None,
        max_length=128,
        description="Registry name for the resulting model; defaults to the experiment slug.",
    )
    hyperparameters: dict[str, float | int | str] = Field(default_factory=dict)
    use_automl: bool = False

    @field_validator("framework")
    @classmethod
    def _known_framework(cls, value: str) -> str:
        if value not in _ALLOWED_FRAMEWORKS:
            raise ValueError(f"framework must be one of {sorted(_ALLOWED_FRAMEWORKS)}")
        return value


class RunOut(BaseModel):
    """A training run's status snapshot."""

    run_id: str
    experiment_id: str
    status: RunStatus
    metrics: dict[str, float] = Field(default_factory=dict)
