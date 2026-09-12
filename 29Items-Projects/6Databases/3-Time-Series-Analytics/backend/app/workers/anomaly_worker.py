"""Periodic anomaly scan over the device fleet.

Run:  python -m app.workers.anomaly_worker
Deploy: worker image target, different command (docker-compose `anomaly-worker`).

Per (device, metric): fetch rollup history → Prophet/z-score detection →
persist to `anomalies` (idempotent upsert on the primary key, so a rescan of
the same window is harmless) → publish on Redis `anomalies:{device_id}`.
One bad series never stalls the fleet scan (skip-and-continue).

Fleet sharding: replica N of M handles devices where crc32(device_id) % M == N
(WORKER_SHARD_INDEX / WORKER_SHARD_COUNT).
"""

from __future__ import annotations

import asyncio
import logging
import time
import zlib
from datetime import UTC, datetime, timedelta

from app.core.config import settings
from app.core.logging import configure_logging
from app.db import cassandra, influx, redis
from app.repositories import anomalies as anomalies_repo
from app.repositories import devices as devices_repo
from app.repositories import metrics as metrics_repo
from app.repositories import series_catalog
from app.services import aggregation, anomaly_detection

logger = logging.getLogger(__name__)

LOOKBACK_HOURS = 72
RAW_FALLBACK_HOURS = 6  # young series: no rollups yet, scan recent raw instead


def in_shard(device_id: str) -> bool:
    return (zlib.crc32(device_id.encode()) % settings.worker_shard_count) == (
        settings.worker_shard_index % settings.worker_shard_count
    )


async def scan_series(device_id: str, metric: str, start: datetime, end: datetime) -> int:
    series = await metrics_repo.query_rollup_1h(device_id, metric, start, end)
    if not series:
        series = await metrics_repo.query_raw(
            device_id, metric, end - timedelta(hours=RAW_FALLBACK_HOURS), end
        )

    history = [(p.ts, p.value) for p in series]
    # Prophet fit is CPU-bound → thread, so the loop stays responsive.
    found = await asyncio.to_thread(anomaly_detection.detect, device_id, metric, history)
    if not found:
        return 0

    await anomalies_repo.insert_many(found)
    for anomaly in found:
        await aggregation.publish_anomaly_event(device_id, anomaly.model_dump_json())
    return len(found)


async def scan_once() -> int:
    end = datetime.now(UTC)
    start = end - timedelta(hours=LOOKBACK_HOURS)
    total = 0
    for device in await devices_repo.list_all():
        if not device.enabled or not in_shard(device.device_id):
            continue
        try:
            metric_names = await series_catalog.metrics_for(device.device_id)
        except Exception:
            logger.warning(
                "catalog lookup failed for %s — skipping", device.device_id, exc_info=True
            )
            continue
        for metric in metric_names:
            try:
                total += await scan_series(device.device_id, metric, start, end)
            except Exception:
                logger.warning(
                    "scan failed for %s/%s — continuing",
                    device.device_id,
                    metric,
                    exc_info=True,
                )
    return total


async def main() -> None:
    configure_logging(settings.log_level, settings.log_format)
    await cassandra.connect()
    await redis.connect()
    try:
        await influx.connect()
    except Exception as exc:
        logger.warning("influx unavailable — worker telemetry disabled: %s", exc)

    logger.info(
        "anomaly worker started (interval=%ss, method=%s, shard %d/%d)",
        settings.detection_interval_seconds,
        settings.detection_method,
        settings.worker_shard_index,
        settings.worker_shard_count,
    )
    try:
        while True:
            started = time.perf_counter()
            try:
                found = await scan_once()
                influx.write_point(
                    "anomaly_worker",
                    fields={
                        "anomalies_found": found,
                        "scan_duration_s": time.perf_counter() - started,
                    },
                    tags={"env": settings.environment},
                )
                logger.info("scan complete: %d anomalies", found)
            except Exception:
                logger.exception("scan crashed; retrying next interval")
            await asyncio.sleep(settings.detection_interval_seconds)
    finally:
        await influx.close()
        await redis.close()
        await cassandra.close()


if __name__ == "__main__":
    import contextlib

    with contextlib.suppress(KeyboardInterrupt):
        asyncio.run(main())
