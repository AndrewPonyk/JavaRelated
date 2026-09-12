#!/usr/bin/env python
"""Synthetic log generator — sends realistic traffic into the platform.

    python scripts/seed_sample_logs.py --count 500 --burst-errors          # via gateway (default)
    python scripts/seed_sample_logs.py --count 500 --mode kafka            # straight to logs.raw

Gateway mode exercises the full edge (auth, validation, 202 semantics); kafka mode
bypasses it for pipeline-only testing. Also handy for demos and e2e tests.
"""

from __future__ import annotations

import argparse
import asyncio
import random
import sys
import time
from datetime import datetime, timedelta, timezone

import httpx

SERVICES = ["checkout", "payments", "inventory", "auth", "search", "notifications"]
HOSTS = [f"ip-10-0-{i}-{j}" for i in (1, 2) for j in (11, 12, 13)]

NORMAL_MESSAGES = [
    ("INFO", "request completed method=GET path=/api/v1/{svc} status=200 duration_ms={n}"),
    ("INFO", "request completed method=POST path=/api/v1/{svc} status=201 duration_ms={n}"),
    ("DEBUG", "cache hit key={svc}:item:{n}"),
    ("INFO", "background job finished job={svc}-sync items={n}"),
    ("WARN", "slow query detected duration_ms={n} table={svc}_events"),
]

ERROR_MESSAGES = [
    ("ERROR", "upstream timeout calling {svc}-db after {n}ms"),
    ("ERROR", "unhandled exception processing request: ConnectionResetError"),
    ("FATAL", "circuit breaker OPEN for dependency {svc}-gateway"),
    ("ERROR", "failed to persist event id={n}: version conflict"),
]


def make_event(ts: datetime, *, error_burst_service: str | None) -> dict:
    service = random.choice(SERVICES)
    if error_burst_service and service == error_burst_service and random.random() < 0.7:
        level, template = random.choice(ERROR_MESSAGES)
    else:
        level, template = random.choices(
            NORMAL_MESSAGES + ERROR_MESSAGES, weights=[30, 20, 25, 10, 5, 1, 1, 1, 1]
        )[0]
    return {
        "timestamp": ts.isoformat(),
        "service": service,
        "env": "dev",
        "level": level,
        "message": template.format(svc=service, n=random.randint(1, 5000)),
        "host": random.choice(HOSTS),
        "trace_id": f"{random.getrandbits(64):016x}",
        "attributes": {"region": "eu-central-1", "version": "1.4.2"},
    }


def make_batches(count: int, batch_size: int, burst_service: str | None) -> list[list[dict]]:
    now = datetime.now(timezone.utc)
    batches: list[list[dict]] = []
    remaining = count
    while remaining > 0:
        size = min(batch_size, remaining)
        # Spread timestamps over the last few minutes so windows have shape.
        batches.append(
            [
                make_event(
                    now - timedelta(seconds=random.uniform(0, 240)),
                    error_burst_service=burst_service,
                )
                for _ in range(size)
            ]
        )
        remaining -= size
    return batches


def send_via_gateway(args: argparse.Namespace, batches: list[list[dict]]) -> int:
    headers = {"X-API-Key": args.api_key} if args.api_key else {}
    sent = 0
    with httpx.Client(timeout=10.0, headers=headers) as client:
        for batch in batches:
            try:
                resp = client.post(f"{args.gateway}/v1/logs", json=batch)
            except httpx.HTTPError as exc:
                print(f"ERROR: gateway unreachable at {args.gateway}: {exc}", file=sys.stderr)
                return 2
            if resp.status_code != 202:
                print(
                    f"ERROR: gateway returned {resp.status_code}: {resp.text[:300]}",
                    file=sys.stderr,
                )
                return 1
            body = resp.json()
            sent += len(batch)
            print(f"sent {sent} " f"(accepted={body['accepted']} rejected={body['rejected']})")
            if args.rate:
                time.sleep(args.rate)
    return 0


def send_via_kafka(args: argparse.Namespace, batches: list[list[dict]]) -> int:
    """Produce validated events straight to logs.raw (bypasses the gateway)."""
    from pydantic import ValidationError

    from log_analytics.common.kafka import TOPIC_LOGS_RAW, make_producer
    from log_analytics.common.models import LogEvent
    from log_analytics.common.parsing import coerce_log_record

    async def _run() -> int:
        producer = make_producer(args.bootstrap, client_id="seed-script")
        try:
            await producer.start()
        except Exception as exc:
            print(f"ERROR: Kafka unreachable at {args.bootstrap}: {exc}", file=sys.stderr)
            return 2
        sent = 0
        try:
            for batch in batches:
                for raw in batch:
                    try:
                        event = LogEvent.model_validate(coerce_log_record(raw))
                    except ValidationError:
                        continue
                    await producer.send(TOPIC_LOGS_RAW, value=event, key=event.service)
                await producer.flush()
                sent += len(batch)
                print(f"produced {sent} to {TOPIC_LOGS_RAW}")
                if args.rate:
                    await asyncio.sleep(args.rate)
        finally:
            await producer.stop()
        return 0

    return asyncio.run(_run())


def main() -> int:
    import os

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=["gateway", "kafka"], default="gateway")
    parser.add_argument("--gateway", default="http://localhost:8080")
    parser.add_argument(
        "--bootstrap", default=os.getenv("LA_KAFKA_BOOTSTRAP_SERVERS", "localhost:29092")
    )
    parser.add_argument("--api-key", default="", help="X-API-Key if the gateway has auth enabled")
    parser.add_argument("--count", type=int, default=500)
    parser.add_argument("--batch-size", type=int, default=100)
    parser.add_argument("--rate", type=float, default=0.0, help="seconds to sleep between batches")
    parser.add_argument(
        "--burst-errors",
        action="store_true",
        help="one random service produces a heavy error burst (demo for detection/ML)",
    )
    parser.add_argument("--burst-service", default="", help="pin the bursting service by name")
    args = parser.parse_args()

    burst_service = args.burst_service or (random.choice(SERVICES) if args.burst_errors else None)
    if burst_service:
        print(f"error burst service: {burst_service}")

    batches = make_batches(args.count, args.batch_size, burst_service)
    if args.mode == "kafka":
        return send_via_kafka(args, batches)
    return send_via_gateway(args, batches)


if __name__ == "__main__":
    sys.exit(main())
