"""Ingest orchestration.

Order of operations per batch:
  1. policy filter   — drop too-old / future-dated points (counted as rejected)
  2. authorization   — device-key batches may only carry their own device_id;
                       unknown/disabled devices are rejected point-by-point
  3. rate limit      — per-device points/minute budget (Redis, fail-open)
  4. system of record— Cassandra write + series-catalog upsert (failures = 5xx)
  5. side effects    — Redis live windows and Influx telemetry, best-effort

Only step 4 is transactional (docs/ARCHITECTURE.md §2.6): live aggregates and
telemetry log on failure but never fail the request.
"""

from __future__ import annotations

import asyncio
import logging
import time
from collections import Counter
from datetime import UTC, datetime, timedelta

from app.core.config import settings
from app.db import BackendUnavailableError, influx, redis
from app.repositories import metrics as metrics_repo
from app.repositories import series_catalog
from app.schemas.metric import IngestBatch, IngestResult, MetricPoint
from app.services import aggregation, device_registry

logger = logging.getLogger(__name__)


class DeviceScopeError(Exception):
    """A device-scoped key tried to write another device's data (→ 403)."""


class RateLimitExceeded(Exception):
    """A device exceeded INGEST_RATE_LIMIT_PER_MINUTE (→ 429)."""

    def __init__(self, device_id: str) -> None:
        super().__init__(f"Rate limit exceeded for device {device_id}")
        self.device_id = device_id


# In-process guard so the catalog upsert runs once per series per process,
# not once per batch. Bounded; Cassandra INSERT is idempotent anyway.
_known_series: set[tuple[str, str]] = set()
_KNOWN_SERIES_CAP = 20_000


def partition_by_age(
    points: list[MetricPoint], now: datetime | None = None
) -> tuple[list[MetricPoint], list[MetricPoint]]:
    """Split points into (fresh, stale) per the late-data/skew policy."""
    current = now or datetime.now(UTC)
    oldest = current - timedelta(hours=settings.ingest_max_age_hours)
    newest = current + timedelta(minutes=settings.ingest_future_tolerance_minutes)
    fresh: list[MetricPoint] = []
    stale: list[MetricPoint] = []
    for point in points:
        (fresh if oldest <= point.ts <= newest else stale).append(point)
    return fresh, stale


async def _authorized_points(
    points: list[MetricPoint], key_device_id: str | None
) -> tuple[list[MetricPoint], int]:
    """Drop points for unknown/disabled devices; enforce key scope."""
    device_ids = {p.device_id for p in points}
    if key_device_id is not None and device_ids - {key_device_id}:
        raise DeviceScopeError(f"Key for {key_device_id} cannot write data for other devices")

    allowed: dict[str, bool] = {}
    for device_id in device_ids:
        allowed[device_id] = await device_registry.ingest_allowed(device_id)

    kept = [p for p in points if allowed[p.device_id]]
    return kept, len(points) - len(kept)


async def _enforce_rate_limit(points: list[MetricPoint]) -> None:
    """Per-device sliding-minute budget in Redis. Fails OPEN: a Redis outage
    must not stop ingestion (Cassandra is the component that matters)."""
    limit = settings.ingest_rate_limit_per_minute
    if limit <= 0:
        return
    try:
        client = redis.get_client()
        minute = int(time.time() // 60)
        per_device = Counter(p.device_id for p in points)
        for device_id, count in per_device.items():
            key = f"rl:{device_id}:{minute}"
            total = await client.incrby(key, count)
            await client.expire(key, 120)
            if total > limit:
                raise RateLimitExceeded(device_id)
    except (BackendUnavailableError, OSError):
        logger.warning("rate limiter unavailable — allowing batch")


async def ingest_batch(batch: IngestBatch, key_device_id: str | None = None) -> IngestResult:
    """Persist a batch. `key_device_id` is set when a per-device key was used
    (gateway/shared keys pass None and may carry multiple devices)."""
    started = time.perf_counter()

    fresh, stale = partition_by_age(batch.points)
    rejected = len(stale)

    accepted_points: list[MetricPoint] = []
    if fresh:
        accepted_points, unauthorized = await _authorized_points(fresh, key_device_id)
        rejected += unauthorized

    if accepted_points:
        await _enforce_rate_limit(accepted_points)

        # System of record: raw points + series catalog together.
        new_pairs = {
            (p.device_id, p.metric)
            for p in accepted_points
            if (p.device_id, p.metric) not in _known_series
        }
        await asyncio.gather(
            metrics_repo.insert_points(accepted_points),
            series_catalog.register_many(new_pairs),
        )
        if len(_known_series) > _KNOWN_SERIES_CAP:
            _known_series.clear()
        _known_series.update(new_pairs)

        # Live aggregates — best effort.
        try:
            for point in accepted_points:
                await aggregation.record_point(point)
        except BackendUnavailableError:
            logger.warning("redis down: live aggregates skipped for this batch")
        except Exception:
            logger.warning("live aggregate update failed", exc_info=True)

    # Platform telemetry — fire-and-forget by design.
    influx.write_point(
        "ingest",
        fields={
            "points": len(accepted_points),
            "rejected": rejected,
            "latency_ms": (time.perf_counter() - started) * 1000.0,
        },
        tags={"env": settings.environment},
    )
    return IngestResult(accepted=len(accepted_points), rejected=rejected)
