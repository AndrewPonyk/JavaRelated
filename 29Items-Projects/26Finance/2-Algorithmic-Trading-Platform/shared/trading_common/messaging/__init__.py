"""Messaging: a backend-agnostic pub/sub bus over the canonical topics."""

from trading_common.messaging import topics
from trading_common.messaging.bus import (
    InMemoryBus,
    MessageBus,
    Subscription,
    decode,
    encode,
)


def make_bus(kafka_bootstrap_servers: str | None, group_id: str = "trading-platform") -> MessageBus:
    """Factory: Kafka backend when a broker is configured, else in-process."""
    if kafka_bootstrap_servers:
        from trading_common.messaging.kafka_bus import KafkaBus

        return KafkaBus(kafka_bootstrap_servers, group_id)
    return InMemoryBus()


__all__ = [
    "InMemoryBus",
    "MessageBus",
    "Subscription",
    "decode",
    "encode",
    "make_bus",
    "topics",
]
