"""Anomaly feed endpoints."""

from __future__ import annotations

from datetime import UTC, datetime, timedelta
from typing import Annotated

from fastapi import APIRouter, Depends, Query

from app.api.deps import get_current_user
from app.repositories import anomalies as anomalies_repo
from app.schemas.anomaly import Anomaly

router = APIRouter(dependencies=[Depends(get_current_user)])


@router.get("/{device_id}/anomalies", response_model=list[Anomaly])
async def list_anomalies(
    device_id: str,
    start: Annotated[datetime | None, Query()] = None,
    end: Annotated[datetime | None, Query()] = None,
    limit: Annotated[int, Query(ge=1, le=500)] = 100,
) -> list[Anomaly]:
    """Anomalies for a device, newest first (default: last 24h)."""
    end_ts = end.astimezone(UTC) if end else datetime.now(UTC)
    start_ts = start.astimezone(UTC) if start else end_ts - timedelta(hours=24)
    items = await anomalies_repo.query(device_id, start_ts, end_ts)
    return items[:limit]


# Live push variant: GET /api/v1/events/anomalies (SSE, see api/v1/events.py).
