"""Kafka helpers: serialization contracts and client factory configuration."""

from __future__ import annotations

import asyncio
import json
from datetime import datetime, timezone

from log_analytics.common.kafka import (
    TOPIC_PARTITIONS,
    json_deserializer,
    json_serializer,
    make_consumer,
    make_producer,
    security_kwargs,
)
from log_analytics.common.models import LogEvent


def test_serializer_handles_datetimes_and_roundtrips() -> None:
    payload = {"ts": datetime(2026, 7, 1, 12, 0, tzinfo=timezone.utc), "n": 1}
    raw = json_serializer(payload)
    parsed = json_deserializer(raw)
    assert parsed == {"ts": "2026-07-01T12:00:00+00:00", "n": 1}


def test_serializer_dumps_pydantic_models() -> None:
    event = LogEvent.model_validate(
        {"timestamp": "2026-07-01T12:00:00Z", "service": "checkout", "message": "hello"}
    )
    parsed = json.loads(json_serializer(event))
    assert parsed["service"] == "checkout"
    assert parsed["level"] == "INFO"


def test_topology_partitions_cover_all_topics() -> None:
    assert set(TOPIC_PARTITIONS) == {
        "logs.raw",
        "logs.enriched",
        "logs.anomalies",
        "alerts.events",
        "logs.dlq",
    }
    assert all(p >= 1 for p in TOPIC_PARTITIONS.values())


def test_security_kwargs_plaintext_is_empty() -> None:
    assert security_kwargs() == {}
    assert security_kwargs(security_protocol="PLAINTEXT") == {}


def test_security_kwargs_sasl_scram() -> None:
    kwargs = security_kwargs(
        security_protocol="SASL_SSL",
        sasl_mechanism="SCRAM-SHA-512",
        sasl_username="user",
        sasl_password="pw",
    )
    assert kwargs["security_protocol"] == "SASL_SSL"
    assert kwargs["sasl_mechanism"] == "SCRAM-SHA-512"
    assert kwargs["sasl_plain_username"] == "user"


def test_factories_build_configured_clients_without_connecting() -> None:
    async def build() -> tuple[str, str]:
        producer = make_producer("broker:9092", client_id="test-client")
        consumer = make_consumer("t1", bootstrap_servers="broker:9092", group_id="g1")
        try:
            return producer.client._client_id, consumer._group_id
        finally:
            await producer.client.close()
            await consumer._client.close()

    client_id, group_id = asyncio.run(build())
    assert client_id == "test-client"
    assert group_id == "g1"
