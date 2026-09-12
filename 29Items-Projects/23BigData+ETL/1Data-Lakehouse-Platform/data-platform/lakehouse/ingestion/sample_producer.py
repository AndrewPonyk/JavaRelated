"""Local development helper: publish fake order events to Kafka.

Run:
    python -m lakehouse.ingestion.sample_producer --count 100
"""

from __future__ import annotations

import argparse
import json
import random
import uuid
from datetime import UTC, datetime

from lakehouse.common.config import LakehouseSettings
from lakehouse.common.logging import get_logger

STATUSES = ["created", "paid", "shipped", "cancelled"]
CURRENCIES = ["EUR", "USD"]


def make_order() -> dict:
    """One synthetic event matching the orders.v1 contract (see bronze_to_silver)."""
    return {
        "order_id": str(uuid.uuid4()),
        "customer_id": f"c-{random.randint(1, 500):05d}",
        "order_ts": datetime.now(tz=UTC).isoformat(),
        "status": random.choice(STATUSES),
        "amount": round(random.uniform(5, 500), 2),
        "currency": random.choice(CURRENCIES),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--count", type=int, default=100)
    args = parser.parse_args()

    settings = LakehouseSettings.from_env()
    log = get_logger("sample_producer", topic=settings.orders_topic)

    # kafka-python is fine for a dev tool; production producers should use
    # confluent-kafka with idempotence enabled and schema-registry serialization.
    from kafka import KafkaProducer

    producer = KafkaProducer(
        bootstrap_servers=settings.kafka_bootstrap_servers,
        value_serializer=lambda v: json.dumps(v).encode("utf-8"),
        key_serializer=lambda k: k.encode("utf-8"),
    )

    for _ in range(args.count):
        order = make_order()
        producer.send(settings.orders_topic, key=order["order_id"], value=order)

    producer.flush()
    log.info("published sample events", extra={"context": {"count": args.count}})


if __name__ == "__main__":
    main()
