"""Anomaly alert feed: Redis-backed rolling store with acknowledgements.

Fed by the background Kafka consumer (alerts_consumer.py) that mirrors
alerts.anomaly.v1 into Redis; the archive copy lives in
RAW.METADATA.ANOMALY_ALERTS (written by the cloud alerts bridge).

Keys:
    alerts:items    hash — alert_id → alert json
    alerts:recent   zset — alert_id, score = triggered_at_ms (rolling cap)
    alerts:acked    set  — acknowledged alert ids
"""

from __future__ import annotations

import json
import logging
from datetime import UTC, datetime

import redis.asyncio as aioredis

from app.config import get_settings
from app.schemas.alert import Alert, AlertSeverity

log = logging.getLogger(__name__)

ITEMS_KEY = "alerts:items"
RECENT_KEY = "alerts:recent"
ACKED_KEY = "alerts:acked"


class AlertService:
    def __init__(self, redis_url: str | None = None, client: aioredis.Redis | None = None) -> None:
        settings = get_settings()
        self._redis_url = redis_url or settings.redis_url
        self._cap = settings.alerts_recent_cap
        self._redis: aioredis.Redis | None = client

    def _client(self) -> aioredis.Redis:
        if self._redis is None:
            self._redis = aioredis.from_url(self._redis_url, decode_responses=True)
        return self._redis

    async def add(self, alert: dict) -> None:
        """Store one alert envelope (as published by the stream processor)."""
        alert_id = alert.get("alert_id")
        triggered_at_ms = alert.get("triggered_at_ms")
        if not alert_id or triggered_at_ms is None:
            log.warning("discarding malformed alert envelope: %s", alert)
            return
        client = self._client()
        await client.hset(ITEMS_KEY, alert_id, json.dumps(alert))
        await client.zadd(RECENT_KEY, {alert_id: float(triggered_at_ms)})
        await self._trim(client)

    async def _trim(self, client: aioredis.Redis) -> None:
        doomed = await client.zrange(RECENT_KEY, 0, -(self._cap + 1))
        if doomed:
            await client.zrem(RECENT_KEY, *doomed)
            await client.hdel(ITEMS_KEY, *doomed)
            await client.srem(ACKED_KEY, *doomed)

    async def recent(self, limit: int = 50) -> list[Alert]:
        client = self._client()
        ids = await client.zrevrange(RECENT_KEY, 0, limit - 1)
        if not ids:
            return []
        raw_items = await client.hmget(ITEMS_KEY, ids)
        acked = set(await client.smembers(ACKED_KEY))
        alerts: list[Alert] = []
        for raw in raw_items:
            if not raw:
                continue
            try:
                data = json.loads(raw)
                alerts.append(
                    Alert(
                        alert_id=data["alert_id"],
                        metric=data["metric"],
                        value=float(data["value"]),
                        score=float(data["score"]),
                        severity=AlertSeverity(data.get("severity", "warning")),
                        message=data.get("message", ""),
                        triggered_at=datetime.fromtimestamp(data["triggered_at_ms"] / 1000, tz=UTC),
                        acknowledged=data["alert_id"] in acked,
                    )
                )
            except (json.JSONDecodeError, KeyError, TypeError, ValueError) as exc:
                # Third parties can publish to the alerts topic; a bad envelope
                # must degrade to a missing feed item, never a 500.
                log.warning("skipping malformed stored alert: %s", exc)
        return alerts

    async def acknowledge(self, alert_id: str) -> bool:
        """Mark an alert acknowledged; False when the alert is unknown/expired."""
        client = self._client()
        if not await client.hexists(ITEMS_KEY, alert_id):
            return False
        await client.sadd(ACKED_KEY, alert_id)
        return True


_service: AlertService | None = None


def get_alert_service() -> AlertService:
    global _service
    if _service is None:
        _service = AlertService()
    return _service
