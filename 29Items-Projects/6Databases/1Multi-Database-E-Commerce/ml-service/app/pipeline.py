"""
Kafka pipeline: consume ``review.events`` (produced by catalog-service), score
each review's sentiment, and emit ``review.scored`` (consumed back by
catalog-service). Values are plain JSON, so this interoperates with Spring's
JsonSerializer/JsonDeserializer without any cross-language type coupling.
"""
from __future__ import annotations

import asyncio
import json
import logging
import os
from datetime import datetime, timezone
from uuid import uuid4

from aiokafka import AIOKafkaConsumer, AIOKafkaProducer

from .sentiment import analyzer

logger = logging.getLogger(__name__)

REVIEW_EVENTS_TOPIC = "review.events"
REVIEW_SCORED_TOPIC = "review.scored"


class ReviewSentimentPipeline:
    """Long-running consumer→scorer→producer, managed by the app lifespan."""

    def __init__(self, bootstrap_servers: str | None = None) -> None:
        self._bootstrap = bootstrap_servers or os.getenv(
            "KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"
        )
        self._consumer: AIOKafkaConsumer | None = None
        self._producer: AIOKafkaProducer | None = None
        self._task: asyncio.Task | None = None

    async def start(self) -> None:
        self._consumer = AIOKafkaConsumer(
            REVIEW_EVENTS_TOPIC,
            bootstrap_servers=self._bootstrap,
            group_id="ml-service",
            auto_offset_reset="earliest",
            value_deserializer=lambda b: json.loads(b.decode("utf-8")),
        )
        self._producer = AIOKafkaProducer(
            bootstrap_servers=self._bootstrap,
            value_serializer=lambda v: json.dumps(v).encode("utf-8"),
        )
        await self._consumer.start()
        await self._producer.start()
        self._task = asyncio.create_task(self._run())
        logger.info("Review sentiment pipeline started (broker=%s)", self._bootstrap)

    async def _run(self) -> None:
        assert self._consumer is not None and self._producer is not None
        try:
            async for message in self._consumer:
                await self._handle(message.value)
        except asyncio.CancelledError:  # graceful shutdown
            raise
        except Exception:  # keep the loop alive on a bad message
            logger.exception("Error processing review event")

    async def _handle(self, event: dict) -> None:
        result = analyzer.analyze(event.get("text", ""))
        scored = {
            "eventId": str(uuid4()),
            "correlationId": event.get("correlationId"),
            "version": 1,
            "occurredAt": datetime.now(timezone.utc).isoformat(),
            "reviewId": event["reviewId"],
            "productId": event["productId"],
            "label": result.label,
            "score": result.score,
        }
        await self._producer.send_and_wait(
            REVIEW_SCORED_TOPIC, scored, key=event["productId"].encode("utf-8")
        )
        logger.info("Scored review %s -> %s (%.2f)", event["reviewId"], result.label, result.score)

    async def stop(self) -> None:
        if self._task:
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass
        if self._consumer:
            await self._consumer.stop()
        if self._producer:
            await self._producer.stop()
        logger.info("Review sentiment pipeline stopped")
