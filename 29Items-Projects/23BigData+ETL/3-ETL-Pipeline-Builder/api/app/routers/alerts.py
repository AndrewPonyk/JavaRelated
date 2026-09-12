"""Anomaly alert feed for the dashboard + acknowledgement."""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query

from app.schemas.alert import Alert
from app.services.alert_service import AlertService, get_alert_service

router = APIRouter(prefix="/alerts", tags=["alerts"])


@router.get("", response_model=list[Alert])
async def list_recent_alerts(
    limit: int = Query(default=50, ge=1, le=500),
    service: AlertService = Depends(get_alert_service),
) -> list[Alert]:
    return await service.recent(limit=limit)


@router.post("/{alert_id}/ack", response_model=dict)
async def acknowledge_alert(
    alert_id: str,
    service: AlertService = Depends(get_alert_service),
) -> dict:
    acknowledged = await service.acknowledge(alert_id)
    if not acknowledged:
        raise HTTPException(status_code=404, detail="alert not found or expired")
    return {"alert_id": alert_id, "acknowledged": True}
