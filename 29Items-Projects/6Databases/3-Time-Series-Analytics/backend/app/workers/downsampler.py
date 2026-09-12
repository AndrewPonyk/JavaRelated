"""Hourly downsampler: raw points → metrics_rollup_1h (min/max/avg/sum/count).

Run:      python -m app.workers.downsampler                # follow mode (hourly)
Backfill: python -m app.workers.downsampler --backfill 48  # re-rollup 48h, exit

Rollups power long-range chart queries (>48h) and anomaly-model history.
Rollup rows are idempotent upserts, so re-processing a window after an outage
is safe. Uses the same fleet sharding as the anomaly worker.
"""

from __future__ import annotations

import argparse
import asyncio
import logging
from datetime import UTC, datetime, timedelta

from app.core.config import settings
from app.core.logging import configure_logging
from app.db import cassandra
from app.repositories import devices as devices_repo
from app.repositories import metrics as metrics_repo
from app.repositories import series_catalog
from app.workers.anomaly_worker import in_shard

logger = logging.getLogger(__name__)


def previous_full_hour(now: datetime) -> tuple[datetime, datetime]:
    top = now.replace(minute=0, second=0, microsecond=0)
    return top - timedelta(hours=1), top


async def rollup_series(device_id: str, metric: str, start: datetime, end: datetime) -> bool:
    points = await metrics_repo.query_raw(device_id, metric, start, end)
    if not points:
        return False
    values = [p.value for p in points]
    await metrics_repo.insert_rollup_1h(
        device_id,
        metric,
        start,  # rollup row is stamped with the hour start
        vmin=min(values),
        vmax=max(values),
        avg=sum(values) / len(values),
        vsum=sum(values),
        count=len(values),
    )
    return True


async def run_window(start: datetime, end: datetime) -> int:
    rolled = 0
    for device in await devices_repo.list_all():
        if not in_shard(device.device_id):
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
                if await rollup_series(device.device_id, metric, start, end):
                    rolled += 1
            except Exception:
                logger.warning(
                    "rollup failed for %s/%s — continuing",
                    device.device_id,
                    metric,
                    exc_info=True,
                )
    logger.info("downsampled window %s → %s: %d series", start, end, rolled)
    return rolled


async def backfill(hours: int) -> None:
    """Re-rollup every full hour in the past `hours` (post-outage recovery)."""
    _, newest = previous_full_hour(datetime.now(UTC))
    for offset in range(hours, 0, -1):
        start = newest - timedelta(hours=offset)
        await run_window(start, start + timedelta(hours=1))


async def follow() -> None:
    while True:
        try:
            start, end = previous_full_hour(datetime.now(UTC))
            await run_window(start, end)
        except Exception:
            logger.exception("downsample run crashed; retrying next hour")
        # Sleep until 5 past the next hour (grace for in-flight writes).
        now = datetime.now(UTC)
        next_run = now.replace(minute=5, second=0, microsecond=0)
        if next_run <= now:
            next_run += timedelta(hours=1)
        await asyncio.sleep((next_run - now).total_seconds())


async def main(backfill_hours: int | None) -> None:
    configure_logging(settings.log_level, settings.log_format)
    await cassandra.connect()
    try:
        if backfill_hours:
            await backfill(backfill_hours)
        else:
            await follow()
    finally:
        await cassandra.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--backfill",
        type=int,
        metavar="HOURS",
        default=None,
        help="re-rollup the past N hours, then exit",
    )
    args = parser.parse_args()
    import contextlib

    with contextlib.suppress(KeyboardInterrupt):
        asyncio.run(main(args.backfill))
