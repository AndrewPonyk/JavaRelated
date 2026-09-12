"""Metric query endpoints: catalog, historical series, live Redis aggregates."""

from __future__ import annotations

import math
from datetime import UTC, datetime, timedelta
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Query, status

from app.api.deps import get_current_user
from app.core.config import settings
from app.repositories import metrics as metrics_repo
from app.repositories import series_catalog
from app.schemas.metric import LiveAggregate, MetricSeries, SeriesPoint
from app.services import aggregation

router = APIRouter(dependencies=[Depends(get_current_user)])


def _utc(dt: datetime) -> datetime:
    return dt.replace(tzinfo=UTC) if dt.tzinfo is None else dt.astimezone(UTC)


def decimate(points: list[SeriesPoint], max_points: int) -> tuple[list[SeriesPoint], bool]:
    """Stride-sample an oversized series, always keeping the last point."""
    if len(points) <= max_points:
        return points, False
    stride = math.ceil(len(points) / max_points)
    sampled = points[::stride]
    if sampled and sampled[-1].ts != points[-1].ts:
        sampled.append(points[-1])
    return sampled, True


@router.get("/{device_id}/metrics", response_model=list[str])
async def list_metrics(device_id: str) -> list[str]:
    """Metric names this device has ever reported (series catalog)."""
    return await series_catalog.metrics_for(device_id)


@router.get("/{device_id}/metrics/{metric}", response_model=MetricSeries)
async def get_series(
    device_id: str,
    metric: str,
    start: Annotated[datetime | None, Query()] = None,
    end: Annotated[datetime | None, Query()] = None,
) -> MetricSeries:
    """Range query with automatic raw/rollup routing.

    Ranges wider than ROLLUP_QUERY_THRESHOLD_HOURS are served from hourly
    rollups: bounded partitions, bounded payloads (docs/ARCHITECTURE.md §2.4).
    Oversized responses are stride-decimated to MAX_SERIES_POINTS.
    """
    end_ts = _utc(end) if end else datetime.now(UTC)
    start_ts = _utc(start) if start else end_ts - timedelta(hours=1)
    if start_ts >= end_ts:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, detail="start must be < end")

    use_rollup = (end_ts - start_ts) > timedelta(hours=settings.rollup_query_threshold_hours)
    if use_rollup:
        points = await metrics_repo.query_rollup_1h(device_id, metric, start_ts, end_ts)
    else:
        points = await metrics_repo.query_raw(device_id, metric, start_ts, end_ts)

    points, was_decimated = decimate(points, settings.max_series_points)
    return MetricSeries(
        device_id=device_id,
        metric=metric,
        source="rollup_1h" if use_rollup else "raw",
        points=points,
        decimated=was_decimated,
    )


@router.get("/{device_id}/metrics/{metric}/live", response_model=LiveAggregate)
async def get_live(device_id: str, metric: str) -> LiveAggregate:
    """Current 1-minute window straight from Redis — never touches Cassandra."""
    return await aggregation.get_live(device_id, metric)
