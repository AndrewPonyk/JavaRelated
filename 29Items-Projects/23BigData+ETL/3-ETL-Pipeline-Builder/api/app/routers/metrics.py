"""Metrics endpoints.

Hot reads come from Redis (degraded ⇒ 503, never silently stale); history
serves the rolling live window (Redis) or daily buckets (Snowflake marts /
warmed cache). The WebSocket is push-only — no client payloads accepted.
"""

from __future__ import annotations

import contextlib
import logging
from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, Query, WebSocket, WebSocketDisconnect
from redis.exceptions import RedisError

from app.schemas.metric import CurrentMetric, MetricHistoryPoint
from app.services.metrics_service import MetricsService, get_metrics_service

log = logging.getLogger(__name__)

router = APIRouter(prefix="/metrics", tags=["metrics"])


@router.get("/current", response_model=list[CurrentMetric])
async def list_current_metrics(
    service: MetricsService = Depends(get_metrics_service),
) -> list[CurrentMetric]:
    try:
        return await service.list_current()
    except RedisError as exc:
        raise HTTPException(status_code=503, detail="hot metric store unavailable") from exc


@router.get("/{metric_name}/current", response_model=CurrentMetric)
async def get_current_metric(
    metric_name: str,
    service: MetricsService = Depends(get_metrics_service),
) -> CurrentMetric:
    try:
        current = await service.get_current(metric_name)
    except RedisError as exc:
        raise HTTPException(status_code=503, detail="hot metric store unavailable") from exc
    if current is None:
        raise HTTPException(status_code=404, detail=f"unknown or cold metric: {metric_name}")
    return current


@router.get("/{metric_name}/history", response_model=list[MetricHistoryPoint])
async def get_metric_history(
    metric_name: str,
    start: datetime | None = None,
    end: datetime | None = None,
    limit: int = Query(default=1000, ge=1, le=10_000),
    granularity: str = Query(default="live", pattern="^(live|daily)$"),
    service: MetricsService = Depends(get_metrics_service),
) -> list[MetricHistoryPoint]:
    try:
        return await service.history(metric_name, start, end, limit, granularity)
    except RedisError as exc:
        raise HTTPException(status_code=503, detail="hot metric store unavailable") from exc


@router.websocket("/stream")
async def stream_metrics(
    websocket: WebSocket,
    service: MetricsService = Depends(get_metrics_service),
) -> None:
    """Push every hot-store update to the client. Server → client only.

    Auth note: the shared require_auth dependency runs for this route too
    (router-level dependency, HTTPConnection-based) — browsers pass
    ?api_key=/?token= since they cannot set WS headers.
    """
    await websocket.accept()
    try:
        async for message in service.stream():
            await websocket.send_json(message)
    except WebSocketDisconnect:
        pass
    except RedisError as exc:
        log.warning("metric stream lost redis: %s", exc)
    finally:
        with contextlib.suppress(RuntimeError):  # already closed by the client
            await websocket.close()
