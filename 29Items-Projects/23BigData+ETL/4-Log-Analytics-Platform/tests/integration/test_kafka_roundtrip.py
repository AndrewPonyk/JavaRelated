"""Integration: produce/consume round-trip through a live Kafka broker.

Needs `docker compose up -d kafka` (skips otherwise). Run: pytest -m integration
"""

from __future__ import annotations

import asyncio
import os
import uuid
from datetime import datetime, timezone

import pytest

pytestmark = pytest.mark.integration

aiokafka = pytest.importorskip("aiokafka")

BOOTSTRAP = os.getenv("LA_KAFKA_BOOTSTRAP_SERVERS", "localhost:29092")


async def _roundtrip(topic: str, events: list[dict]) -> list[dict]:
    from log_analytics.common.kafka import json_deserializer, json_serializer

    producer = aiokafka.AIOKafkaProducer(
        bootstrap_servers=BOOTSTRAP, value_serializer=json_serializer
    )
    try:
        await producer.start()
    except Exception:
        pytest.skip(f"Kafka not reachable at {BOOTSTRAP}")
    try:
        for event in events:
            await producer.send_and_wait(topic, event)
    finally:
        await producer.stop()

    consumer = aiokafka.AIOKafkaConsumer(
        topic,
        bootstrap_servers=BOOTSTRAP,
        group_id=f"it-{uuid.uuid4().hex[:8]}",
        auto_offset_reset="earliest",
        value_deserializer=json_deserializer,
    )
    await consumer.start()
    received: list[dict] = []
    try:
        async for message in consumer:
            received.append(message.value)
            if len(received) == len(events):
                break
    finally:
        await consumer.stop()
    return received


def test_json_events_survive_the_wire() -> None:
    topic = f"it.roundtrip.{uuid.uuid4().hex[:8]}"  # fresh topic (auto-create is on locally)
    sent = [
        {"timestamp": datetime.now(timezone.utc).isoformat(), "service": "it", "n": i}
        for i in range(3)
    ]
    received = asyncio.run(asyncio.wait_for(_roundtrip(topic, sent), timeout=30))
    assert received == sent
