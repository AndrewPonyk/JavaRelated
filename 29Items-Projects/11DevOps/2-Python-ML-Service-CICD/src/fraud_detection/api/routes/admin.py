"""Administrative endpoints: retraining jobs and drift inspection."""

from __future__ import annotations

from fastapi import APIRouter, Depends, Query, status
from sqlalchemy.orm import Session

from fraud_detection.api.deps import (
    get_db_session,
    get_drift_detector,
    get_retraining_service,
)
from fraud_detection.core.errors import NotFoundError
from fraud_detection.db.repositories import DriftReportRepository
from fraud_detection.schemas.model import (
    DriftReportListResponse,
    DriftReportRecord,
    DriftSummary,
    RetrainJobStatus,
    RetrainRequest,
    RetrainResponse,
)
from fraud_detection.services.drift_detector import DriftDetector
from fraud_detection.services.retraining import RetrainingService

router = APIRouter(prefix="/admin", tags=["admin"])


@router.post("/retrain", status_code=status.HTTP_202_ACCEPTED, response_model=RetrainResponse)
def trigger_retraining(
    payload: RetrainRequest | None = None,
    service: RetrainingService = Depends(get_retraining_service),
) -> RetrainResponse:
    """Manually trigger model retraining (bypasses the drift debounce)."""
    reason = payload.reason if payload is not None else "manual"
    job = service.submit(reason=reason, debounce=False)
    assert job is not None  # manual submissions are never debounced
    return RetrainResponse(
        job_id=job.job_id, status=job.status, reason=job.reason, submitted_at=job.submitted_at
    )


@router.get("/retrain/{job_id}", response_model=RetrainJobStatus)
def retraining_status(
    job_id: str,
    service: RetrainingService = Depends(get_retraining_service),
) -> RetrainJobStatus:
    """Return the status of a retraining job."""
    job = service.get(job_id)
    if job is None:
        raise NotFoundError(f"unknown retraining job {job_id!r}")
    return RetrainJobStatus(
        job_id=job.job_id,
        status=job.status,  # type: ignore[arg-type]
        reason=job.reason,
        submitted_at=job.submitted_at,
        finished_at=job.finished_at,
        detail=job.detail,
        result=job.result,
    )


@router.get("/drift", response_model=DriftSummary)
def drift_status(
    detector: DriftDetector = Depends(get_drift_detector),
    session: Session = Depends(get_db_session),
) -> DriftSummary:
    """Evaluate drift of recent production features against the champion baseline."""
    return detector.evaluate(session)


@router.get("/drift/reports", response_model=DriftReportListResponse)
def drift_reports(
    limit: int = Query(default=50, ge=1, le=500),
    session: Session = Depends(get_db_session),
) -> DriftReportListResponse:
    """Return the most recent persisted drift reports."""
    items = DriftReportRepository(session).list(limit=limit)
    return DriftReportListResponse(items=[DriftReportRecord.model_validate(item) for item in items])
