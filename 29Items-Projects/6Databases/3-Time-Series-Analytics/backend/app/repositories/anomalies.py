"""Data access for detected anomalies (partitioned by device + day, TTL 90d)."""

from __future__ import annotations

import asyncio
from datetime import UTC, datetime

from app.db import cassandra
from app.repositories.metrics import day_buckets
from app.schemas.anomaly import Anomaly

_INSERT = (
    "INSERT INTO anomalies "
    "(device_id, bucket, ts, metric, value, expected, lower, upper, score, method) "
    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
)
_SELECT = (
    "SELECT ts, metric, value, expected, lower, upper, score, method "
    "FROM anomalies WHERE device_id = ? AND bucket = ? AND ts >= ? AND ts <= ?"
)


async def insert_many(items: list[Anomaly]) -> None:
    await asyncio.gather(
        *(
            cassandra.execute(
                _INSERT,
                (
                    a.device_id,
                    a.ts.date(),
                    a.ts,
                    a.metric,
                    a.value,
                    a.expected,
                    a.lower,
                    a.upper,
                    a.score,
                    a.method,
                ),
            )
            for a in items
        )
    )


async def query(device_id: str, start: datetime, end: datetime) -> list[Anomaly]:
    results = await asyncio.gather(
        *(
            cassandra.execute(_SELECT, (device_id, bucket, start, end))
            for bucket in day_buckets(start, end)
        )
    )
    items = [
        Anomaly(
            device_id=device_id,
            metric=row.metric,
            ts=row.ts.replace(tzinfo=UTC),
            value=row.value,
            expected=row.expected,
            lower=row.lower,
            upper=row.upper,
            score=row.score,
            method=row.method,
        )
        for rows in results
        for row in rows
    ]
    items.sort(key=lambda a: a.ts, reverse=True)
    return items
