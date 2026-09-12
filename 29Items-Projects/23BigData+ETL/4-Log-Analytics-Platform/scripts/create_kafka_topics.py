#!/usr/bin/env python
"""Create the platform's Kafka topics with the partition counts from common/kafka.py.

    python scripts/create_kafka_topics.py [--bootstrap localhost:29092]

Idempotent: existing topics are reported and left untouched. Runs in deploys too —
partition *increases* are a manual, deliberate operation (TODO: --alter flag).
"""

from __future__ import annotations

import argparse
import asyncio
import os
import sys

from aiokafka.admin import AIOKafkaAdminClient, NewTopic
from aiokafka.errors import TopicAlreadyExistsError

from log_analytics.common.kafka import REPLICATION_FACTOR, TOPIC_PARTITIONS


async def create_topics(bootstrap: str) -> int:
    admin = AIOKafkaAdminClient(bootstrap_servers=bootstrap)
    await admin.start()
    try:
        for topic, partitions in TOPIC_PARTITIONS.items():
            new_topic = NewTopic(
                name=topic,
                num_partitions=partitions,
                replication_factor=REPLICATION_FACTOR,
                topic_configs={
                    "retention.ms": str(3 * 24 * 60 * 60 * 1000),  # 3 days; raw archive lives in S3
                    "compression.type": "producer",
                },
            )
            try:
                await admin.create_topics([new_topic])
                print(f"created  {topic} ({partitions} partitions)")
            except TopicAlreadyExistsError:
                print(f"exists   {topic}")
    finally:
        await admin.close()
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--bootstrap",
        default=os.getenv("LA_KAFKA_BOOTSTRAP_SERVERS", "localhost:29092"),
    )
    args = parser.parse_args()
    return asyncio.run(create_topics(args.bootstrap))


if __name__ == "__main__":
    sys.exit(main())
