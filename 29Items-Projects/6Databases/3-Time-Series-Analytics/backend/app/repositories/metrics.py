"""Data access for metrics_raw / metrics_rollup_1h.

Schema contract lives in backend/migrations/cassandra/002_metrics_tables.cql:
raw partitions are (device_id, metric, DAY), rollups (device_id, metric, MONTH).
Every query here hits explicit partitions — no ALLOW FILTERING, ever.
"""

from __future__ import annotations

import asyncio
from datetime import UTC, date, datetime, timedelta

from app.db import cassandra
from app.schemas.metric import MetricPoint, SeriesPoint

_INSERT_RAW = (
    "INSERT INTO metrics_raw (device_id, metric, bucket, ts, value, tags) "
    "VALUES (?, ?, ?, ?, ?, ?)"
)
_SELECT_RAW = (
    "SELECT ts, value FROM metrics_raw "
    "WHERE device_id = ? AND metric = ? AND bucket = ? AND ts >= ? AND ts <= ?"
)
_INSERT_ROLLUP = (
    "INSERT INTO metrics_rollup_1h "
    "(device_id, metric, bucket, ts, min, max, avg, sum, count) "
    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
)
_SELECT_ROLLUP = (
    "SELECT ts, avg, min, max FROM metrics_rollup_1h "
    "WHERE device_id = ? AND metric = ? AND bucket = ? AND ts >= ? AND ts <= ?"
)


def day_buckets(start: datetime, end: datetime) -> list[date]:
    """Every day partition touched by [start, end]."""
    days = []
    d = start.date()
    while d <= end.date():
        days.append(d)
        d += timedelta(days=1)
    return days


def month_buckets(start: datetime, end: datetime) -> list[str]:
    """Every month partition ('YYYY-MM') touched by [start, end]."""
    months = []
    y, m = start.year, start.month
    while (y, m) <= (end.year, end.month):
        months.append(f"{y:04d}-{m:02d}")
        y, m = (y + 1, 1) if m == 12 else (y, m + 1)
    return months


async def insert_points(points: list[MetricPoint]) -> None:
    """Write a batch of raw points (system of record — errors propagate).

    Points are grouped by partition (device, metric, day) and each group is
    written as a single-partition UNLOGGED batch — one round trip per
    partition. Never a logged/cross-partition Cassandra BATCH (anti-pattern).
    """
    by_partition: dict[tuple[str, str, date], list[tuple]] = {}
    for p in points:
        key = (p.device_id, p.metric, p.ts.date())
        by_partition.setdefault(key, []).append(
            (p.device_id, p.metric, p.ts.date(), p.ts, p.value, p.tags or None)
        )

    await asyncio.gather(
        *(
            cassandra.execute(_INSERT_RAW, rows[0])
            if len(rows) == 1
            else cassandra.execute_unlogged_batch(_INSERT_RAW, rows)
            for rows in by_partition.values()
        )
    )


async def query_raw(
    device_id: str, metric: str, start: datetime, end: datetime
) -> list[SeriesPoint]:
    results = await asyncio.gather(
        *(
            cassandra.execute(_SELECT_RAW, (device_id, metric, bucket, start, end))
            for bucket in day_buckets(start, end)
        )
    )
    points = [
        SeriesPoint(ts=row.ts.replace(tzinfo=UTC), value=row.value)
        for rows in results
        for row in rows
    ]
    points.sort(key=lambda p: p.ts)
    return points


async def query_rollup_1h(
    device_id: str, metric: str, start: datetime, end: datetime
) -> list[SeriesPoint]:
    results = await asyncio.gather(
        *(
            cassandra.execute(_SELECT_ROLLUP, (device_id, metric, bucket, start, end))
            for bucket in month_buckets(start, end)
        )
    )
    points = [
        SeriesPoint(ts=row.ts.replace(tzinfo=UTC), value=row.avg)
        for rows in results
        for row in rows
    ]
    points.sort(key=lambda p: p.ts)
    return points


async def insert_rollup_1h(
    device_id: str,
    metric: str,
    ts: datetime,
    *,
    vmin: float,
    vmax: float,
    avg: float,
    vsum: float,
    count: int,
) -> None:
    bucket = f"{ts.year:04d}-{ts.month:02d}"
    await cassandra.execute(
        _INSERT_ROLLUP,
        (device_id, metric, bucket, ts, vmin, vmax, avg, vsum, count),
    )
