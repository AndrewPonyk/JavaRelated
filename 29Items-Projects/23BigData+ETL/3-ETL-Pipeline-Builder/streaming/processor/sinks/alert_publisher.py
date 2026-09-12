"""Anomaly alert fan-out: alerts.anomaly.v1 topic.

Downstream consumers: the API's alert feed (Redis-backed) and, in cloud
environments, the SNS bridge that pages Slack/PagerDuty. Business anomalies
are a SEPARATE channel from pipeline failures — different audiences,
different urgency (docs/ARCHITECTURE.md §2.6).
"""

from __future__ import annotations

import json
import logging
import time
import uuid

from processor.anomaly.detector import AnomalyResult

log = logging.getLogger(__name__)


class AlertPublisher:
    """Publishes one alert per metric per cooldown window (noise control).

    Accepts an already-started producer (shared with the DLQ) or creates its
    own when constructed with just a bootstrap string.
    """

    def __init__(
        self,
        bootstrap_servers: str | None = None,
        topic: str = "alerts.anomaly.v1",
        cooldown_seconds: int = 60,
        producer=None,
    ) -> None:
        if producer is None and not bootstrap_servers:
            raise ValueError("either producer or bootstrap_servers is required")
        self._bootstrap = bootstrap_servers
        self._topic = topic
        self._cooldown = cooldown_seconds
        self._last_sent: dict[str, float] = {}
        self._producer = producer
        self._owns_producer = producer is None
        self.alerts_published = 0

    async def start(self) -> None:
        if self._owns_producer and self._producer is None:
            from aiokafka import AIOKafkaProducer

            self._producer = AIOKafkaProducer(bootstrap_servers=self._bootstrap)
            await self._producer.start()

    async def stop(self) -> None:
        if self._owns_producer and self._producer is not None:
            await self._producer.stop()
            self._producer = None

    async def publish(self, result: AnomalyResult) -> bool:
        """Publish the alert; returns False when suppressed by the cooldown."""
        now = time.monotonic()
        if now - self._last_sent.get(result.metric, -1e12) < self._cooldown:
            return False
        self._last_sent[result.metric] = now

        alert = {
            "alert_id": uuid.uuid4().hex,
            "metric": result.metric,
            "value": result.value,
            "score": round(result.score, 3),
            "baseline_mean": round(result.baseline_mean, 6),
            "severity": "critical" if result.score >= 8 else "warning",
            "message": (
                f"{result.metric} = {result.value:.4g} deviates from baseline "
                f"{result.baseline_mean:.4g} (z={result.score:.1f})"
            ),
            "triggered_at_ms": int(time.time() * 1000),
        }
        assert self._producer is not None, "call start() first"
        await self._producer.send_and_wait(self._topic, json.dumps(alert).encode())
        self.alerts_published += 1
        log.warning("anomaly alert published: %s", alert["message"])
        return True
