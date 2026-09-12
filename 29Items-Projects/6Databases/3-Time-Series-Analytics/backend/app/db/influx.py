"""InfluxDB client — platform telemetry ONLY (never device data of record).

Writes are fire-and-forget: telemetry failure must never fail a request
(docs/ARCHITECTURE.md §2.6 principle 3).
"""

from __future__ import annotations

import asyncio
import logging
from typing import Any

from app.core.config import settings
from app.db import BackendUnavailableError

logger = logging.getLogger(__name__)

_client: Any = None
_write_api: Any = None


async def connect() -> None:
    global _client, _write_api
    if _client is not None:
        return
    from influxdb_client import InfluxDBClient

    client = InfluxDBClient(
        url=settings.influxdb_url,
        token=settings.influxdb_token,
        org=settings.influxdb_org,
    )
    if not await asyncio.to_thread(client.ping):
        client.close()
        raise BackendUnavailableError("InfluxDB ping failed")
    _client = client
    _write_api = client.write_api()  # batching mode: buffers + flushes in background


def is_connected() -> bool:
    return _client is not None


def write_point(
    measurement: str,
    fields: dict[str, float | int],
    tags: dict[str, str] | None = None,
) -> None:
    """Best-effort telemetry write; silently no-ops when Influx is down.

    Keep tag values LOW-cardinality (site/env/route template), never device_id
    (docs/TECH-NOTES.md §3.6 pitfall 7).
    """
    if _write_api is None:
        return
    try:
        from influxdb_client import Point

        point = Point(measurement)
        for tag_key, tag_value in (tags or {}).items():
            point = point.tag(tag_key, tag_value)
        for field_key, field_value in fields.items():
            point = point.field(field_key, field_value)
        _write_api.write(bucket=settings.influxdb_bucket, record=point)
    except Exception:
        logger.debug("influx telemetry write failed", exc_info=True)


async def close() -> None:
    global _client, _write_api
    # Close the batching write API first so buffered points flush while the
    # interpreter is fully alive (otherwise its scheduler flushes at atexit).
    if _write_api is not None:
        await asyncio.to_thread(_write_api.close)
    if _client is not None:
        await asyncio.to_thread(_client.close)
    _client = None
    _write_api = None
