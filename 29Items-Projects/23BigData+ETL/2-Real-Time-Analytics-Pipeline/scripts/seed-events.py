#!/usr/bin/env python3
"""Synthetic BusinessEvent generator for local development and load smoke tests.

Produces JSON events matching the BusinessEvent contract (streaming/flink-common)
to events.raw.v1, with occasional injected anomaly bursts so the anomaly job and
the alerts UI have something real to detect.

Usage:
    python scripts/seed-events.py --rate 25                 # ~25 events/s, Ctrl+C to stop
    python scripts/seed-events.py --rate 200 --burst-every 30

Targets the local compose broker (plaintext). Seeding an MSK dev cluster requires the
IAM SASL variant (aws-msk-iam-sasl-signer-python) — AWS-gated alongside the infra.
"""
import argparse
import asyncio
import json
import random
import time
import uuid

from aiokafka import AIOKafkaProducer

TOPIC = "events.raw.v1"

# (eventType, source, base value, jitter)
EVENT_TYPES = [
    ("orders.completed", "checkout-service", 80.0, 30.0),
    ("payments.captured", "payment-service", 120.0, 60.0),
    ("users.signup", "identity-service", 1.0, 0.0),
]
REGIONS = ["eu-central", "eu-west", "us-east"]
CHANNELS = ["web", "mobile", "api"]


def make_event(now_ms: int, burst: bool) -> tuple[bytes, bytes]:
    event_type, source, base, jitter = random.choice(EVENT_TYPES)
    value = max(0.5, random.gauss(base, jitter or 0.1))
    if burst:
        value *= 10  # the anomaly the EWMA detector should catch
    event = {
        "eventId": str(uuid.uuid4()),
        "eventType": event_type,
        "source": source,
        # small negative jitter simulates producer/network disorder (watermark budget)
        "occurredAt": now_ms - random.randint(0, 2000),
        "value": round(value, 2),
        "dimensions": {
            "region": random.choice(REGIONS),
            "channel": random.choice(CHANNELS),
        },
        "schemaVersion": 1,
    }
    key = event["eventId"].encode()  # entity id in real producers
    return key, json.dumps(event).encode()


async def run(rate: float, bootstrap: str, burst_every: float, burst_seconds: float) -> None:
    producer = AIOKafkaProducer(
        bootstrap_servers=bootstrap,
        acks="all",
        enable_idempotence=True,  # producer-side dedup into the broker
        linger_ms=5,
    )
    await producer.start()
    sent, burst_until, next_burst = 0, 0.0, time.monotonic() + burst_every
    print(f"producing ~{rate}/s to {TOPIC} via {bootstrap} (burst every {burst_every}s)")
    try:
        while True:
            now = time.monotonic()
            if now >= next_burst:
                burst_until = now + burst_seconds
                next_burst = now + burst_every
                print(f"⚡ anomaly burst for {burst_seconds}s")
            key, value = make_event(int(time.time() * 1000), burst=now < burst_until)
            await producer.send_and_wait(TOPIC, value, key=key)
            sent += 1
            if sent % 500 == 0:
                print(f"  sent {sent} events")
            await asyncio.sleep(1.0 / rate)
    except asyncio.CancelledError:
        pass
    finally:
        await producer.stop()
        print(f"done — {sent} events")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rate", type=float, default=25.0, help="events per second")
    parser.add_argument("--bootstrap", default="localhost:29092", help="host listener of the compose broker")
    parser.add_argument("--burst-every", type=float, default=60.0, help="seconds between anomaly bursts")
    parser.add_argument("--burst-seconds", type=float, default=5.0, help="burst duration")
    args = parser.parse_args()
    try:
        asyncio.run(run(args.rate, args.bootstrap, args.burst_every, args.burst_seconds))
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
