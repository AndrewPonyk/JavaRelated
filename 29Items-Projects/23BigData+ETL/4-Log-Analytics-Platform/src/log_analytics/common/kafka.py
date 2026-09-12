"""Kafka topology constants and client factories.

Topic names and partition counts live here — the single place to change them.
`scripts/create_kafka_topics.py` applies this topology; Spark jobs and services import it.

aiokafka imports are deferred so this module stays importable inside Spark executors
(which don't ship the client library).
"""

from __future__ import annotations

import json
from datetime import datetime
from typing import TYPE_CHECKING, Any

if TYPE_CHECKING:  # pragma: no cover
    from aiokafka import AIOKafkaConsumer, AIOKafkaProducer

# ── Topology ─────────────────────────────────────────────────────────────────

TOPIC_LOGS_RAW = "logs.raw"  # gateway → pipeline (as received, validated)
TOPIC_LOGS_ENRICHED = "logs.enriched"  # canonical normalized stream
TOPIC_LOGS_ANOMALIES = "logs.anomalies"  # AnomalyRecord per service-window
TOPIC_ALERTS = "alerts.events"  # AlertEvent from pattern detection
TOPIC_LOGS_DLQ = "logs.dlq"  # poison messages + error reason

# Partitions bound Spark read parallelism; keyed by `service` for per-service ordering.
TOPIC_PARTITIONS: dict[str, int] = {
    TOPIC_LOGS_RAW: 12,
    TOPIC_LOGS_ENRICHED: 12,
    TOPIC_LOGS_ANOMALIES: 3,
    TOPIC_ALERTS: 3,
    TOPIC_LOGS_DLQ: 3,
}

# Local/dev = 1 (single broker). TODO: 3 in staging/prod (MSK, 3 AZ).
REPLICATION_FACTOR = 1


# ── Serialization ────────────────────────────────────────────────────────────


def _json_default(obj: Any) -> str:
    if isinstance(obj, datetime):
        return obj.isoformat()
    return str(obj)


def json_serializer(value: Any) -> bytes:
    """Canonical JSON bytes for Kafka message values (pydantic models or dicts)."""
    if hasattr(value, "model_dump"):
        value = value.model_dump(mode="json")
    return json.dumps(value, default=_json_default, separators=(",", ":")).encode("utf-8")


def json_deserializer(raw: bytes) -> Any:
    return json.loads(raw.decode("utf-8"))


# ── Client factories ─────────────────────────────────────────────────────────


def security_kwargs(
    security_protocol: str = "PLAINTEXT",
    sasl_mechanism: str = "",
    sasl_username: str = "",
    sasl_password: str = "",
) -> dict[str, Any]:
    """aiokafka connection-security kwargs from settings (MSK: SASL_SSL + SCRAM-SHA-512).

    MSK IAM auth needs the OAUTHBEARER signer package and is wired at deploy time;
    SASL/SCRAM + TLS is the supported in-code path.
    """
    kwargs: dict[str, Any] = {}
    if security_protocol and security_protocol != "PLAINTEXT":
        kwargs["security_protocol"] = security_protocol
    if sasl_mechanism:
        kwargs["sasl_mechanism"] = sasl_mechanism
        kwargs["sasl_plain_username"] = sasl_username
        kwargs["sasl_plain_password"] = sasl_password
    return kwargs


def make_producer(
    bootstrap_servers: str,
    client_id: str = "log-analytics",
    **security: Any,
) -> AIOKafkaProducer:
    """Producer tuned for log traffic: batched, compressed, acks=all."""
    from aiokafka import AIOKafkaProducer

    return AIOKafkaProducer(
        bootstrap_servers=bootstrap_servers,
        **security_kwargs(**security),
        client_id=client_id,
        value_serializer=json_serializer,
        key_serializer=lambda k: k.encode("utf-8") if isinstance(k, str) else k,
        acks="all",
        linger_ms=20,
        compression_type="gzip",
        request_timeout_ms=15_000,
    )


def make_consumer(
    *topics: str,
    bootstrap_servers: str,
    group_id: str,
    **security: Any,
) -> AIOKafkaConsumer:
    """Consumer with manual commit (commit AFTER successful handling, never before)."""
    from aiokafka import AIOKafkaConsumer

    return AIOKafkaConsumer(
        *topics,
        bootstrap_servers=bootstrap_servers,
        **security_kwargs(**security),
        group_id=group_id,
        value_deserializer=json_deserializer,
        enable_auto_commit=False,
        auto_offset_reset="earliest",
    )
