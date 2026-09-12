"""Background consumer: mirrors alerts.anomaly.v1 into the Redis alert store.

Resilient by design — the API must serve traffic even when Kafka is down, so
connection failures degrade to an empty/stale alert feed plus warnings, never
a crashed app. Runs as a lifespan task (see app/main.py)."""

from __future__ import annotations

import asyncio
import contextlib
import json
import logging
from collections.abc import Callable

from app.config import Settings
from app.services.alert_service import AlertService

log = logging.getLogger(__name__)


class AlertsConsumer:
    def __init__(
        self,
        settings: Settings,
        store: AlertService,
        consumer_factory: Callable | None = None,
        retry_seconds: float = 5.0,
    ) -> None:
        self._settings = settings
        self._store = store
        self._consumer_factory = consumer_factory or self._default_factory
        self._retry_seconds = retry_seconds
        self.alerts_ingested = 0

    def _default_factory(self):
        from aiokafka import AIOKafkaConsumer

        return AIOKafkaConsumer(
            self._settings.kafka_alerts_topic,
            bootstrap_servers=self._settings.kafka_bootstrap_servers,
            group_id="api-alerts-feed",
            auto_offset_reset="earliest",  # backfill the feed after API restarts
            enable_auto_commit=True,
        )

    async def run(self, stop: asyncio.Event) -> None:
        while not stop.is_set():
            try:
                await self._consume(stop)
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                log.warning(
                    "alerts consumer disconnected (%s); retrying in %ss",
                    exc,
                    self._retry_seconds,
                )
                with contextlib.suppress(TimeoutError):
                    await asyncio.wait_for(stop.wait(), timeout=self._retry_seconds)

    async def _consume(self, stop: asyncio.Event) -> None:
        consumer = self._consumer_factory()
        await consumer.start()
        log.info("alerts consumer started: topic=%s", self._settings.kafka_alerts_topic)
        try:
            while not stop.is_set():
                records = await consumer.getmany(timeout_ms=500, max_records=200)
                for _tp, messages in records.items():
                    for message in messages:
                        await self._ingest(message.value)
        finally:
            await consumer.stop()

    async def _ingest(self, raw: bytes) -> None:
        try:
            alert = json.loads(raw)
        except json.JSONDecodeError as exc:
            log.warning("unparseable alert dropped: %s", exc)
            return
        await self._store.add(alert)
        self.alerts_ingested += 1
