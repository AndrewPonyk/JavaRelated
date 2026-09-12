"""Kafka producer wrapper for validated log events."""

from __future__ import annotations

import logging
from typing import Any

from log_analytics.common.kafka import TOPIC_LOGS_RAW, make_producer
from log_analytics.common.models import LogEvent

logger = logging.getLogger(__name__)


class LogEventProducer:
    """Async producer publishing LogEvents to `logs.raw`, keyed by service.

    Keying by service keeps per-service ordering intact through every downstream
    repartition-free stage — required for sensible windowed metrics.
    """

    def __init__(
        self,
        bootstrap_servers: str,
        client_id: str = "ingestion-gateway",
        **security: Any,
    ) -> None:
        self._producer = make_producer(bootstrap_servers, client_id=client_id, **security)
        self._started = False

    async def start(self) -> None:
        if not self._started:
            await self._producer.start()
            self._started = True
            logger.info("Kafka producer started")

    async def stop(self) -> None:
        if self._started:
            await self._producer.stop()
            self._started = False
            logger.info("Kafka producer stopped")

    async def ready(self) -> bool:
        """Broker connectivity check for /readyz — metadata fetch for the target topic."""
        if not self._started:
            return False
        try:
            partitions = await self._producer.partitions_for(TOPIC_LOGS_RAW)
            return bool(partitions)
        except Exception:  # kafka errors vary by failure mode; readiness only cares yes/no
            return False

    async def send(self, event: LogEvent) -> None:
        await self._producer.send_and_wait(TOPIC_LOGS_RAW, value=event, key=event.service)

    async def send_batch(self, events: list[LogEvent]) -> int:
        """Queue a batch and flush once — one broker round-trip per request, not per event."""
        for event in events:
            await self._producer.send(TOPIC_LOGS_RAW, value=event, key=event.service)
        await self._producer.flush()
        return len(events)
