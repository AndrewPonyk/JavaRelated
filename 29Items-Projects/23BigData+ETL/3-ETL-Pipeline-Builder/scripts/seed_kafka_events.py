"""Synthetic order-event producer for local development and load tests.

Usage:
    python scripts/seed_kafka_events.py --rate 20 --duration 60
    python scripts/seed_kafka_events.py --burst          # inject an anomaly spike

Produces the events.orders.v1 envelope consumed by streaming/processor.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import random
import time
import uuid


def make_event(burst: bool = False) -> dict:
    failed = random.random() < 0.02
    return {
        "event_id": uuid.uuid4().hex,
        "event_type": "checkout_failed" if failed else "order_placed",
        "ts_ms": int(time.time() * 1000),
        "amount": 0.0
        if failed
        else round(random.lognormvariate(3.5, 0.6), 2) * (10 if burst else 1),
        "customer_id": f"c{random.randint(1, 500):04d}",
        "currency": "EUR",
    }


async def produce(bootstrap: str, topic: str, rate: float, duration: float, burst: bool) -> None:
    from aiokafka import AIOKafkaProducer

    producer = AIOKafkaProducer(bootstrap_servers=bootstrap)
    await producer.start()
    sent = 0
    deadline = time.monotonic() + duration
    try:
        while time.monotonic() < deadline:
            # A burst multiplies volume AND amounts → trips the EWMA detector.
            effective_rate = rate * (20 if burst else 1)
            event = make_event(burst=burst)
            await producer.send_and_wait(topic, json.dumps(event).encode())
            sent += 1
            if sent % 100 == 0:
                print(f"sent {sent} events...")
            await asyncio.sleep(1.0 / effective_rate)
    finally:
        await producer.stop()
        # ASCII on purpose: Windows consoles often run cp1252.
        print(f"done: {sent} events -> {topic} @ {bootstrap}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bootstrap", default="localhost:29092")
    parser.add_argument("--topic", default="events.orders.v1")
    parser.add_argument("--rate", type=float, default=20.0, help="events per second")
    parser.add_argument("--duration", type=float, default=60.0, help="seconds to run")
    parser.add_argument("--burst", action="store_true", help="20x volume + 10x amounts (anomaly)")
    args = parser.parse_args()
    asyncio.run(produce(args.bootstrap, args.topic, args.rate, args.duration, args.burst))


if __name__ == "__main__":
    main()
