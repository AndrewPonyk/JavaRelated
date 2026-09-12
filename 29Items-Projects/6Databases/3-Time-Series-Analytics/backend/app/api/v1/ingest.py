"""Device-facing ingest endpoint (write-only credential plane)."""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status

from app.api.deps import IngestPrincipal, require_ingest_principal
from app.core.config import settings
from app.schemas.metric import IngestBatch, IngestResult
from app.services import ingestion

router = APIRouter()


@router.post("", status_code=status.HTTP_202_ACCEPTED, response_model=IngestResult)
async def ingest(
    batch: IngestBatch,
    principal: Annotated[IngestPrincipal, Depends(require_ingest_principal)],
) -> IngestResult:
    """Accept a batch of metric points.

    202 semantics: the system-of-record write (Cassandra) has succeeded for
    `accepted` points; `rejected` counts points dropped by policy (too old,
    future-dated, unknown or disabled device). Live aggregates and telemetry
    are best-effort side effects.
    """
    if len(batch.points) > settings.max_ingest_batch_size:
        raise HTTPException(
            status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail=f"Batch exceeds MAX_INGEST_BATCH_SIZE={settings.max_ingest_batch_size}",
        )
    try:
        return await ingestion.ingest_batch(batch, key_device_id=principal.device_id)
    except ingestion.DeviceScopeError as exc:
        raise HTTPException(status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    except ingestion.RateLimitExceeded as exc:
        raise HTTPException(
            status.HTTP_429_TOO_MANY_REQUESTS,
            detail=str(exc),
            headers={"Retry-After": "60"},
        ) from exc
