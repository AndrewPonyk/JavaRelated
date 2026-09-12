"""Dead-letter queue: quarantine poison messages with error context.

DLQ envelope preserves the raw payload (base64 — it may not be valid UTF-8,
that can be exactly why it's here) so messages can be replayed after a fix.
"""

from __future__ import annotations

import base64
import json
import logging
import time

log = logging.getLogger(__name__)


class DeadLetterPublisher:
    def __init__(self, producer, topic: str, source_topic: str) -> None:
        self._producer = producer
        self._topic = topic
        self._source_topic = source_topic
        self.dead_lettered = 0

    async def publish(self, raw: bytes, error: str) -> None:
        envelope = {
            "source_topic": self._source_topic,
            "error": error[:500],
            "payload_b64": base64.b64encode(raw).decode("ascii"),
            "quarantined_at_ms": int(time.time() * 1000),
        }
        await self._producer.send_and_wait(self._topic, json.dumps(envelope).encode())
        self.dead_lettered += 1
        log.info("message dead-lettered to %s: %s", self._topic, error[:120])
