"""MSK/Kafka consumer with at-least-once semantics and a dead-letter path.

Offsets are committed only AFTER a batch has been fully processed and sunk —
duplicates are possible on crash/rebalance and are handled by idempotent sinks
(Redis: keyed per window; Snowflake: event_id dedupe in dbt staging).

Poison messages are published to the DLQ topic with error context and never
block the stream. "Poison" includes semantically toxic events, not just
unparseable ones:
  * non-finite amounts (NaN/inf) would permanently corrupt EWMA baselines
  * far-future timestamps would advance the watermark and make every
    subsequent legitimate event "late" (silent data loss on the hot path)
"""

from __future__ import annotations

import asyncio
import json
import logging
import math
import time
from collections.abc import AsyncIterator, Awaitable, Callable

from aiokafka import AIOKafkaConsumer

from processor.config import Settings
from processor.metrics_aggregator import Event

log = logging.getLogger(__name__)

DeadLetterFn = Callable[[bytes, str], Awaitable[None]]

# Producer clocks may drift; beyond this the event is quarantined instead of
# being allowed to poison the watermark.
MAX_FUTURE_DRIFT_MS = 60_000


class EventConsumer:
    def __init__(
        self,
        settings: Settings,
        dead_letter: DeadLetterFn | None = None,
        consumer_factory: Callable[[], AIOKafkaConsumer] | None = None,
    ) -> None:
        self._settings = settings
        self._dead_letter = dead_letter
        self._consumer_factory = consumer_factory or self._default_factory
        self.malformed_messages = 0
        self.events_consumed = 0

    def _default_factory(self) -> AIOKafkaConsumer:
        s = self._settings
        return AIOKafkaConsumer(
            s.kafka_events_topic,
            bootstrap_servers=s.kafka_bootstrap_servers,
            group_id=s.kafka_consumer_group,
            enable_auto_commit=False,  # commit-after-process = at-least-once
            auto_offset_reset="latest",
            # On MSK, IAM auth is added via env-provided security settings:
            #   security_protocol="SASL_SSL", sasl_mechanism="AWS_MSK_IAM"
            #   (aws-msk-iam-sasl-signer-python) — configured in the task definition.
        )

    async def stream_batches(self, stop: asyncio.Event) -> AsyncIterator[list[Event]]:
        """Yield parsed event batches until `stop` is set (graceful drain)."""
        consumer = self._consumer_factory()
        await consumer.start()
        log.info(
            "consumer started: topic=%s group=%s",
            self._settings.kafka_events_topic,
            self._settings.kafka_consumer_group,
        )
        try:
            while not stop.is_set():
                records = await consumer.getmany(timeout_ms=50, max_records=500)
                events: list[Event] = []
                for _tp, messages in records.items():
                    for message in messages:
                        event = await self._parse(message.value)
                        if event is not None:
                            events.append(event)
                if events:
                    self.events_consumed += len(events)
                    yield events
                    # Sinks succeeded (caller resumed the generator) → safe to commit.
                    await consumer.commit()
        finally:
            await consumer.stop()
            log.info("consumer stopped (drained)")

    async def _parse(self, raw: bytes) -> Event | None:
        try:
            data = json.loads(raw)
            event = Event(
                event_id=data["event_id"],
                event_type=data["event_type"],
                ts_ms=int(data["ts_ms"]),
                amount=float(data.get("amount", 0.0)),
                attrs={
                    k: v
                    for k, v in data.items()
                    if k not in ("event_id", "event_type", "ts_ms", "amount")
                },
            )
            self._validate(event)
            return event
        except (ValueError, KeyError, TypeError) as exc:
            self.malformed_messages += 1
            log.warning("poison message quarantined to DLQ: %s", exc)
            if self._dead_letter is not None:
                try:
                    await self._dead_letter(raw, str(exc))
                except Exception:  # DLQ failure must never kill the stream
                    log.exception("dead-letter publish failed; message dropped")
            return None

    @staticmethod
    def _validate(event: Event) -> None:
        if not math.isfinite(event.amount):
            raise ValueError(f"non-finite amount {event.amount!r} would poison metric baselines")
        drift_ms = event.ts_ms - int(time.time() * 1000)
        if drift_ms > MAX_FUTURE_DRIFT_MS:
            raise ValueError(
                f"ts_ms is {drift_ms / 1000:.0f}s in the future — would poison the watermark"
            )
