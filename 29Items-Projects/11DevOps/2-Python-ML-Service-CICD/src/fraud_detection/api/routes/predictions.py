"""Fraud scoring and prediction audit endpoints."""

from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from fraud_detection.api.deps import get_db_session, get_prediction_service
from fraud_detection.core.errors import NotFoundError
from fraud_detection.db.repositories import PredictionRepository
from fraud_detection.schemas.prediction import (
    PredictionListResponse,
    PredictionRecord,
    PredictionRequest,
    PredictionResponse,
)
from fraud_detection.services.prediction_service import PredictionService

router = APIRouter(prefix="/predictions", tags=["predictions"])


@router.post("", response_model=PredictionResponse)
def create_prediction(
    payload: PredictionRequest,
    service: PredictionService = Depends(get_prediction_service),
    session: Session = Depends(get_db_session),
) -> PredictionResponse:
    """Score a transaction for fraud using the A/B-selected model variant."""
    return service.predict(payload, session=session)


@router.get("", response_model=PredictionListResponse)
def list_predictions(
    limit: int = Query(default=50, ge=1, le=500),
    offset: int = Query(default=0, ge=0),
    account_id: str | None = Query(default=None),
    session: Session = Depends(get_db_session),
) -> PredictionListResponse:
    """Return a page of persisted predictions (newest first)."""
    items, total = PredictionRepository(session).list(
        limit=limit, offset=offset, account_id=account_id
    )
    return PredictionListResponse(
        items=[PredictionRecord.model_validate(item) for item in items],
        total=total,
        limit=limit,
        offset=offset,
    )


@router.get("/{transaction_id}", response_model=PredictionRecord)
def get_prediction(
    transaction_id: str,
    session: Session = Depends(get_db_session),
) -> PredictionRecord:
    """Return the persisted prediction for one transaction."""
    record = PredictionRepository(session).get_by_transaction_id(transaction_id)
    if record is None:
        raise NotFoundError(f"no prediction recorded for transaction {transaction_id!r}")
    return PredictionRecord.model_validate(record)
