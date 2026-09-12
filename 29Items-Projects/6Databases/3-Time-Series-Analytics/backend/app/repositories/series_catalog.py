"""Data access for the series catalog: which metrics each device reports.

Maintained by the ingest path (upsert — Cassandra INSERT is idempotent),
read by the workers (metric discovery) and the metric-picker endpoint.
"""

from __future__ import annotations

import asyncio

from app.db import cassandra

_UPSERT = "INSERT INTO series_catalog (device_id, metric) VALUES (?, ?)"
_SELECT_METRICS = "SELECT metric FROM series_catalog WHERE device_id = ?"


async def register_many(pairs: set[tuple[str, str]]) -> None:
    if not pairs:
        return
    await asyncio.gather(
        *(cassandra.execute(_UPSERT, (device_id, metric)) for device_id, metric in pairs)
    )


async def metrics_for(device_id: str) -> list[str]:
    rows = await cassandra.execute(_SELECT_METRICS, (device_id,))
    return sorted(row.metric for row in rows)
