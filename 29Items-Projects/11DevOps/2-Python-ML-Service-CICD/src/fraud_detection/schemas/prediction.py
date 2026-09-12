"""Schemas for the fraud scoring endpoints."""

from __future__ import annotations

import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class PredictionRequest(BaseModel):
    """A single transaction to score for fraud."""

    transaction_id: str = Field(min_length=1, description="Unique transaction identifier.")
    account_id: str = Field(
        min_length=1, description="Account the transaction belongs to (A/B routing key)."
    )
    amount: float = Field(gt=0, description="Transaction amount; must be positive.")
    merchant_category: str = Field(min_length=1, description="Merchant category code or name.")
    timestamp: datetime = Field(description="Transaction timestamp (ISO 8601).")
    features: dict[str, float] = Field(
        default_factory=dict, description="Optional pre-computed extra features."
    )


class PredictionResponse(BaseModel):
    """Fraud score for a transaction."""

    model_config = ConfigDict(protected_namespaces=())

    transaction_id: str
    model_version: str
    variant: str = Field(
        description='A/B variant that served the request: "champion"|"challenger".'
    )
    fraud_probability: float = Field(ge=0.0, le=1.0)
    is_fraud: bool
    latency_ms: float


class PredictionRecord(BaseModel):
    """A persisted prediction audit record."""

    model_config = ConfigDict(protected_namespaces=(), from_attributes=True)

    id: uuid.UUID
    transaction_id: str
    account_id: str
    amount: float
    model_version: str
    variant: str
    fraud_probability: float
    is_fraud: bool
    latency_ms: float
    features: dict[str, float] | None = None
    created_at: datetime | None = None


class PredictionListResponse(BaseModel):
    """A page of persisted predictions."""

    items: list[PredictionRecord]
    total: int
    limit: int
    offset: int
