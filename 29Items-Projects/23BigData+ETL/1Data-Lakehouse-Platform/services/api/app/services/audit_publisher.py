"""Best-effort fan-out of audit events to Kafka (`platform.audit.v1`).

The `audit_events` DB row (written in the same transaction as the change) is the
source of truth; Kafka is for alerting/lineage consumers. A publish failure is
logged and never fails the request — hence the deliberate catch-all — and a
short circuit-breaker stops a down broker from adding connect latency to every
request while it is unreachable.
"""

from __future__ import annotations

import json
import logging
import time
from collections.abc import Callable
from functools import lru_cache
from typing import Any, Protocol

from app.core.config import get_settings

log = logging.getLogger("api.audit")

FAILURE_BACKOFF_SECONDS = 60.0


class AuditPublisher(Protocol):
    def publish(self, event: dict[str, Any]) -> None: ...


class NullAuditPublisher:
    """Used when KAFKA_AUDIT_ENABLED is false (local dev, unit tests)."""

    def publish(self, event: dict[str, Any]) -> None:
        return None


class KafkaAuditPublisher:
    """Async-batched producer; `producer_factory` is injectable for tests."""

    def __init__(
        self,
        bootstrap_servers: str,
        topic: str,
        producer_factory: Callable[..., Any] | None = None,
        failure_backoff_seconds: float = FAILURE_BACKOFF_SECONDS,
    ) -> None:
        self._bootstrap_servers = bootstrap_servers
        self._topic = topic
        self._producer_factory = producer_factory or self._default_factory
        self._producer: Any | None = None
        self._failure_backoff_seconds = failure_backoff_seconds
        self._blocked_until = 0.0

    @staticmethod
    def _default_factory(**kwargs: Any) -> Any:
        from kafka import KafkaProducer

        return KafkaProducer(**kwargs)

    def _get_producer(self) -> Any:
        if self._producer is None:
            self._producer = self._producer_factory(
                bootstrap_servers=self._bootstrap_servers,
                value_serializer=lambda v: json.dumps(v, default=str).encode("utf-8"),
                acks=1,
                linger_ms=50,
                # Bound the damage of a down broker: never stall a request
                # thread for more than ~2s, and only once per backoff window.
                max_block_ms=2000,
                request_timeout_ms=5000,
            )
        return self._producer

    def publish(self, event: dict[str, Any]) -> None:
        if time.monotonic() < self._blocked_until:
            return  # broker recently unreachable — skip until the window passes
        try:
            self._get_producer().send(self._topic, value=event)
        except Exception:  # noqa: BLE001 — audit fan-out must never fail a request
            log.warning("audit event fan-out to Kafka failed", exc_info=True)
            self._producer = None  # force a clean reconnect after the backoff
            self._blocked_until = time.monotonic() + self._failure_backoff_seconds


@lru_cache
def get_audit_publisher() -> AuditPublisher:
    settings = get_settings()
    if not settings.kafka_audit_enabled:
        return NullAuditPublisher()
    return KafkaAuditPublisher(
        bootstrap_servers=settings.kafka_bootstrap_servers,
        topic=settings.kafka_audit_topic,
    )
