"""End-to-end pipeline smoke: Kafka → Bronze → Silver → DQ gate → Gold → features.

Runs the real jobs (same entrypoints Airflow calls) against a real Kafka broker,
with the lake on local storage, and asserts row-level outcomes:

  * at-least-once duplicates are deduplicated into Silver (latest event wins)
  * invalid events land in quarantine, not Silver
  * the DQ gate passes (exit-code contract with Airflow)
  * Gold aggregates and the ML feature snapshot materialize
  * job metrics and DQ results are persisted
  * datasets self-register in the governance catalog (when CATALOG_API_URL set)

Intended to run inside the lakehouse-spark container on the compose network:
    scripts/run_spark_docker.sh smoke
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import urllib.request
import uuid
from datetime import UTC, datetime, timedelta

LAKE_ROOT = os.environ.get("SMOKE_LAKE_ROOT", "/tmp/lake-smoke")

VALID_TODAY = 40
VALID_YESTERDAY = 40
DUPLICATED = 5  # re-emitted with a later timestamp and status=paid
INVALID = 6


def configure_environment() -> None:
    shutil.rmtree(LAKE_ROOT, ignore_errors=True)
    os.environ["LAKEHOUSE_LOCAL"] = "1"
    os.environ.setdefault("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
    os.environ["KAFKA_ORDERS_TOPIC"] = f"orders.smoke.{uuid.uuid4().hex[:8]}"
    for layer in ("bronze", "silver", "gold", "artifacts"):
        os.environ[f"LAKE_{layer.upper()}_URI"] = f"file://{LAKE_ROOT}/{layer}"
    os.environ.pop("AWS_ENDPOINT_URL", None)  # lake is on local FS for the smoke


def produce_events() -> tuple[int, str]:
    from kafka import KafkaProducer
    from kafka.admin import KafkaAdminClient, NewTopic

    topic = os.environ["KAFKA_ORDERS_TOPIC"]
    bootstrap = os.environ["KAFKA_BOOTSTRAP_SERVERS"]

    admin = KafkaAdminClient(bootstrap_servers=bootstrap)
    admin.create_topics([NewTopic(topic, num_partitions=3, replication_factor=1)])
    admin.close()

    producer = KafkaProducer(
        bootstrap_servers=bootstrap,
        value_serializer=lambda v: json.dumps(v).encode(),
        key_serializer=lambda k: k.encode(),
    )
    now = datetime.now(tz=UTC)
    sent = 0

    def send(order: dict) -> None:
        nonlocal sent
        producer.send(topic, key=str(order.get("order_id")), value=order)
        sent += 1

    def order(i: int, ts: datetime, status: str = "created") -> dict:
        return {
            "order_id": f"o-{i:05d}",
            "customer_id": f"c-{i % 25:04d}",
            "order_ts": ts.isoformat(),
            "status": status,
            "amount": round(10 + (i % 50) * 3.5, 2),
            "currency": "EUR" if i % 3 else "USD",
        }

    for i in range(VALID_TODAY):
        send(order(i, now))
    for i in range(VALID_TODAY, VALID_TODAY + VALID_YESTERDAY):
        send(order(i, now - timedelta(days=1)))
    for i in range(DUPLICATED):  # later event for the same key must win in Silver
        send(order(i, now + timedelta(minutes=5), status="paid"))
    for i in range(INVALID):
        bad = order(1000 + i, now)
        bad["amount"] = -99.0
        send(bad)

    producer.flush()
    producer.close()
    return sent, topic


def run_pipeline(run_date: str) -> None:
    # Bronze ingestion first: it creates the shared SparkSession *with* the
    # Kafka connector jars; downstream jobs reuse that session via getOrCreate.
    from lakehouse.ingestion import kafka_bronze_stream

    sys.argv = ["kafka_bronze_stream", "--trigger", "available-now"]
    kafka_bronze_stream.main()

    from lakehouse.jobs import bronze_to_silver, silver_to_gold

    bronze_to_silver.run(run_date)

    # The DQ gate runs as a subprocess: its exit code is the Airflow contract.
    gate = subprocess.run(
        [
            sys.executable,
            "-m",
            "lakehouse.quality.checks",
            "--table",
            "silver/sales/orders",
            "--run-date",
            run_date,
        ],
        env=os.environ,
    )
    assert gate.returncode == 0, "DQ gate failed on clean silver data"

    silver_to_gold.run(run_date)

    from lakehouse.features import customer_order_features

    customer_order_features.run(run_date)


def verify_lake(run_date: str) -> list[str]:
    from lakehouse.common.config import LakehouseSettings
    from lakehouse.common.spark import build_spark_session

    settings = LakehouseSettings.from_env()
    spark = build_spark_session("smoke-verify", settings)
    results: list[str] = []

    def check(label: str, actual, expected=None, minimum=None) -> None:
        if expected is not None:
            assert actual == expected, f"{label}: expected {expected}, got {actual}"
        if minimum is not None:
            assert actual >= minimum, f"{label}: expected >= {minimum}, got {actual}"
        results.append(f"  ✓ {label}: {actual}")

    silver = spark.read.format("delta").load(settings.silver_path("sales", "orders"))
    check("silver unique orders", silver.count(), expected=VALID_TODAY + VALID_YESTERDAY)

    dup_statuses = {
        row.status
        for row in silver.filter(silver.order_id.isin([f"o-{i:05d}" for i in range(DUPLICATED)]))
        .select("status")
        .collect()
    }
    check("dedupe keeps latest event (status=paid)", sorted(dup_statuses), expected=["paid"])

    quarantine = spark.read.format("delta").load(settings.quarantine_path("sales", "orders"))
    check("quarantined invalid rows", quarantine.count(), expected=INVALID)

    gold = spark.read.format("delta").load(settings.gold_path("sales", "order_daily_stats"))
    check("gold daily stats rows", gold.filter(gold.event_date == run_date).count(), minimum=1)

    features = spark.read.format("delta").load(settings.gold_path("ml", "customer_order_features"))
    check("feature snapshot rows", features.count(), minimum=1)

    metrics = spark.read.format("delta").load(f"{settings.artifacts_uri}/_metrics/job_runs")
    check("job metrics recorded", metrics.count(), minimum=4)

    dq = spark.read.format("delta").load(f"{settings.artifacts_uri}/_audit/dq_results")
    check("dq results persisted", dq.count(), minimum=4)
    return results


def verify_catalog() -> list[str]:
    base = os.environ.get("CATALOG_API_URL", "").rstrip("/")
    if not base:
        return ["  - catalog check skipped (CATALOG_API_URL not set)"]

    with urllib.request.urlopen(f"{base}/api/v1/datasets?limit=50", timeout=10) as response:
        listing = json.loads(response.read())
    names = {item["name"] for item in listing["items"]}
    expected = {"sales.orders", "sales.order_daily_stats", "ml.customer_order_features"}
    missing = expected - names
    assert not missing, f"datasets not registered in catalog: {missing}"

    results = [f"  ✓ catalog datasets registered: {sorted(expected)}"]
    for item in listing["items"]:
        if item["name"] in expected:
            with urllib.request.urlopen(
                f"{base}/api/v1/datasets/{item['id']}/versions", timeout=10
            ) as response:
                versions = json.loads(response.read())
            assert versions, f"{item['name']} has no registered schema versions"
    results.append("  ✓ every registered dataset has a schema version")
    return results


def main() -> int:
    configure_environment()
    run_date = datetime.now(tz=UTC).date().isoformat()

    print(f"==> producing events (topic={os.environ['KAFKA_ORDERS_TOPIC']})")
    sent, topic = produce_events()
    print(f"    sent {sent} events to {topic}")

    print("==> running medallion pipeline (bronze → silver → DQ → gold → features)")
    run_pipeline(run_date)

    print("==> verifying lake state")
    print("\n".join(verify_lake(run_date)))
    print("==> verifying catalog registration")
    print("\n".join(verify_catalog()))
    print("SMOKE E2E: ALL CHECKS PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
