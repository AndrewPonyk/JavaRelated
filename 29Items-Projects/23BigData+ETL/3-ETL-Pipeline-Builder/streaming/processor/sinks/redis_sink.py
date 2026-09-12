"""Hot-store sink: latest value per metric (hash), rolling live history (zset),
and pub/sub fan-out to the API.

Idempotent by construction: current-value writes are keyed per metric
(last-write-wins) and history members are keyed by window start, so
at-least-once redelivery cannot corrupt the hot store.

Keys (shared contract with api/app/services/metrics_service.py):
    metric:{name}            hash  — latest closed window
    metric:{name}:history    zset  — member=json, score=window_start_ms
    metrics.updates          pub/sub channel
"""

from __future__ import annotations

import json
import time

import redis.asyncio as aioredis

from processor.metrics_aggregator import MetricPoint


class RedisMetricSink:
    def __init__(
        self,
        url: str,
        *,
        channel: str = "metrics.updates",
        key_prefix: str = "metric:",
        ttl_seconds: int = 3600,
        history_retention_minutes: int = 180,
        client: aioredis.Redis | None = None,
    ) -> None:
        self._url = url
        self._channel = channel
        self._prefix = key_prefix
        self._ttl = ttl_seconds
        self._history_retention_ms = history_retention_minutes * 60_000
        self._redis = client

    def _client(self) -> aioredis.Redis:
        if self._redis is None:
            self._redis = aioredis.from_url(self._url, decode_responses=True)
        return self._redis

    async def write(self, point: MetricPoint) -> None:
        payload = {
            "metric": point.metric,
            "value": point.value,
            "window_start_ms": point.window_start_ms,
            "window_ms": point.window_ms,
        }
        serialized = json.dumps(payload)
        current_key = self._prefix + point.metric
        history_key = f"{current_key}:history"
        history_cutoff = int(time.time() * 1000) - self._history_retention_ms

        pipe = self._client().pipeline(transaction=False)
        pipe.hset(current_key, mapping={k: str(v) for k, v in payload.items()})
        pipe.expire(current_key, self._ttl)
        pipe.zadd(history_key, {serialized: point.window_start_ms})
        pipe.zremrangebyscore(history_key, "-inf", history_cutoff)
        pipe.expire(history_key, self._history_retention_ms // 1000)
        pipe.publish(self._channel, serialized)
        await pipe.execute()

    async def close(self) -> None:
        if self._redis is not None:
            await self._redis.aclose()
