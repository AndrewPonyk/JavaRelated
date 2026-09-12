"""Real-time aggregation over Redis.

Model: one hash per (device, metric, 1-minute window) holding
count/sum/min/max, plus a `latest:` key per series. A single Lua script folds
each point atomically (min/max cannot be raced) in one round trip. All keys
are TTL'd — Redis stays rebuildable (docs/ARCHITECTURE.md §2.2).

Window math is pure and unit-tested (tests/unit/test_aggregation.py).
"""

from __future__ import annotations

import logging
from datetime import UTC, datetime

from app.core.config import settings
from app.db import redis
from app.schemas.metric import LiveAggregate, MetricPoint

logger = logging.getLogger(__name__)

# KEYS[1]=window hash, KEYS[2]=latest key
# ARGV[1]=value, ARGV[2]=window ttl, ARGV[3]=latest ttl
_FOLD_POINT_LUA = """
local value = tonumber(ARGV[1])
redis.call('HINCRBYFLOAT', KEYS[1], 'sum', ARGV[1])
redis.call('HINCRBY', KEYS[1], 'count', 1)
local current_min = redis.call('HGET', KEYS[1], 'min')
if current_min == false or value < tonumber(current_min) then
  redis.call('HSET', KEYS[1], 'min', ARGV[1])
end
local current_max = redis.call('HGET', KEYS[1], 'max')
if current_max == false or value > tonumber(current_max) then
  redis.call('HSET', KEYS[1], 'max', ARGV[1])
end
redis.call('EXPIRE', KEYS[1], ARGV[2])
redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[3])
return 1
"""


def window_start(ts: datetime, window_seconds: int | None = None) -> datetime:
    """Floor a timestamp to its aggregation window boundary (UTC)."""
    window = window_seconds or settings.live_window_seconds
    epoch = int(ts.timestamp())
    return datetime.fromtimestamp(epoch - (epoch % window), tz=UTC)


def window_key(device_id: str, metric: str, ts: datetime, window_seconds: int | None = None) -> str:
    start = window_start(ts, window_seconds)
    return f"agg:{device_id}:{metric}:{int(start.timestamp())}"


def latest_key(device_id: str, metric: str) -> str:
    return f"latest:{device_id}:{metric}"


async def record_point(point: MetricPoint) -> None:
    """Fold one point into its live window — atomic, single round trip."""
    client = redis.get_client()
    await client.eval(
        _FOLD_POINT_LUA,
        2,
        window_key(point.device_id, point.metric, point.ts),
        latest_key(point.device_id, point.metric),
        str(point.value),
        str(settings.live_key_ttl_seconds),
        str(settings.live_key_ttl_seconds),
    )


def _maybe_float(raw: str | None) -> float | None:
    return float(raw) if raw is not None else None


async def get_live(device_id: str, metric: str) -> LiveAggregate:
    """Read the current window's stats — O(1), never touches Cassandra."""
    now = datetime.now(UTC)
    client = redis.get_client()
    data = await client.hgetall(window_key(device_id, metric, now))
    latest = await client.get(latest_key(device_id, metric))

    count = int(data.get("count", 0))
    total = float(data.get("sum", 0.0))
    return LiveAggregate(
        device_id=device_id,
        metric=metric,
        window_start=window_start(now),
        count=count,
        sum=total,
        avg=(total / count) if count else None,
        min=_maybe_float(data.get("min")),
        max=_maybe_float(data.get("max")),
        latest=_maybe_float(latest),
    )


async def publish_anomaly_event(device_id: str, payload: str) -> None:
    """Fan out an anomaly notification (consumed by the /events SSE stream)."""
    try:
        await redis.get_client().publish(f"anomalies:{device_id}", payload)
    except Exception:
        logger.warning("anomaly publish failed for %s", device_id, exc_info=True)
