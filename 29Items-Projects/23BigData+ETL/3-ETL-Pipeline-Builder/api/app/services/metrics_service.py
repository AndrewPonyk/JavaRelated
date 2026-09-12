"""Metrics: current values + rolling live history from the Redis hot store,
daily history from Snowflake marts (or the Airflow-warmed Redis daily cache),
live stream via Redis pub/sub relayed over WebSocket.

Redis key contract (shared with streaming/processor/sinks/redis_sink.py and
airflow/dags/common/cache_warmer.py):
    metric:{name}            hash  — latest closed window
    metric:{name}:history    zset  — live points, score = window_start_ms
    metric:{name}:daily      zset  — daily buckets, score = date epoch ms
"""

from __future__ import annotations

import asyncio
import json
import logging
from collections.abc import AsyncIterator
from datetime import UTC, datetime

import redis.asyncio as aioredis

from app.config import get_settings
from app.schemas.metric import CurrentMetric, MetricHistoryPoint

log = logging.getLogger(__name__)

# Metric catalog lives in dbt seed metric_definitions.csv; mirrored here for the
# hot-store scan (a startup load from the warehouse adds a hard dependency the
# hot path must not have).
KNOWN_METRICS: tuple[str, ...] = (
    "orders_per_second",
    "revenue_per_second",
    "avg_order_value",
    "checkout_error_rate",
)

_HISTORY_SQL = (
    "select metric_date, metric_value "
    "from analytics.marts.fct_business_metrics_daily "
    "where metric_name = %s"
)


class MetricsService:
    def __init__(self, redis_url: str | None = None, client: aioredis.Redis | None = None) -> None:
        settings = get_settings()
        self._redis_url = redis_url or settings.redis_url
        self._channel = settings.metrics_channel
        self._prefix = settings.metrics_key_prefix
        self._history_source = settings.history_source
        self._redis: aioredis.Redis | None = client

    def _client(self) -> aioredis.Redis:
        if self._redis is None:
            self._redis = aioredis.from_url(self._redis_url, decode_responses=True)
        return self._redis

    # ── current ────────────────────────────────────────────────────────────
    async def list_current(self) -> list[CurrentMetric]:
        # One round-trip for all metrics (pipelined), not N sequential reads.
        pipe = self._client().pipeline(transaction=False)
        for name in KNOWN_METRICS:
            pipe.hgetall(self._prefix + name)
        results = await pipe.execute()

        out: list[CurrentMetric] = []
        for name, data in zip(KNOWN_METRICS, results, strict=True):
            if not data:
                continue
            try:
                out.append(
                    CurrentMetric(
                        metric=name,
                        value=float(data["value"]),
                        window_start_ms=int(data["window_start_ms"]),
                        window_ms=int(data["window_ms"]),
                    )
                )
            except (KeyError, ValueError) as exc:
                # A corrupt hash must degrade to a missing tile, never a 500.
                log.warning("skipping malformed hot-store hash for %s: %s", name, exc)
        return out

    async def get_current(self, metric: str) -> CurrentMetric | None:
        for item in await self.list_current():
            if item.metric == metric:
                return item
        return None

    # ── history ────────────────────────────────────────────────────────────
    async def history(
        self,
        metric: str,
        start: datetime | None,
        end: datetime | None,
        limit: int,
        granularity: str = "live",
    ) -> list[MetricHistoryPoint]:
        if granularity == "daily" and self._history_source == "snowflake":
            return await asyncio.to_thread(self._daily_from_snowflake, metric, start, end, limit)
        key_suffix = ":daily" if granularity == "daily" else ":history"
        return await self._from_redis_zset(metric, key_suffix, start, end, limit)

    async def _from_redis_zset(
        self,
        metric: str,
        key_suffix: str,
        start: datetime | None,
        end: datetime | None,
        limit: int,
    ) -> list[MetricHistoryPoint]:
        min_score: float | str = int(start.timestamp() * 1000) if start else "-inf"
        max_score: float | str = int(end.timestamp() * 1000) if end else "+inf"
        members = await self._client().zrangebyscore(
            f"{self._prefix}{metric}{key_suffix}", min_score, max_score, start=0, num=limit
        )
        points: list[MetricHistoryPoint] = []
        for member in members:
            try:
                data = json.loads(member)
                points.append(
                    MetricHistoryPoint(
                        metric=metric,
                        bucket_start=datetime.fromtimestamp(data["window_start_ms"] / 1000, tz=UTC),
                        value=float(data["value"]),
                    )
                )
            except (json.JSONDecodeError, KeyError, TypeError, ValueError) as exc:
                log.warning("skipping malformed history member for %s: %s", metric, exc)
        return points

    def _daily_from_snowflake(
        self,
        metric: str,
        start: datetime | None,
        end: datetime | None,
        limit: int,
    ) -> list[MetricHistoryPoint]:
        from app.db import snowflake_client

        sql = _HISTORY_SQL
        params: list = [metric]
        if start is not None:
            sql += " and metric_date >= %s"
            params.append(start.date())
        if end is not None:
            sql += " and metric_date <= %s"
            params.append(end.date())
        sql += f" order by metric_date asc limit {int(limit)}"

        rows = snowflake_client.fetch_all(sql, params)
        return [
            MetricHistoryPoint(
                metric=metric,
                bucket_start=datetime(bucket.year, bucket.month, bucket.day, tzinfo=UTC),
                value=float(value),
            )
            for bucket, value in rows
        ]

    # ── live stream ────────────────────────────────────────────────────────
    async def stream(self) -> AsyncIterator[dict]:
        """Relay hot-store pub/sub messages (dicts ready for WS send_json)."""
        pubsub = self._client().pubsub()
        await pubsub.subscribe(self._channel)
        try:
            async for message in pubsub.listen():
                if message["type"] == "message":
                    yield json.loads(message["data"])
        finally:
            await pubsub.unsubscribe(self._channel)
            await pubsub.aclose()


_service: MetricsService | None = None


def get_metrics_service() -> MetricsService:
    global _service
    if _service is None:
        _service = MetricsService()
    return _service
