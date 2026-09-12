"""Spark transformation tests on a `local[2]` session — no Kafka/OpenSearch needed.

Covers the parse/DLQ split, the enrichment invariants (level normalization, redaction,
idempotent doc ids), and the Python↔Spark contract: schema fields match `LogEvent`,
`template_id` produces identical hashes through Java regex and Python `re`.

Marked integration (JVM spin-up is ~30 s). Native Windows PySpark workers are unreliable
(TECH-NOTES pitfall #11) — on Windows run this file inside the spark image, where pyspark
lives under /opt/spark/python (bare python3 needs it on PYTHONPATH; spark-submit normally
does that):

    MSYS_NO_PATHCONV=1 docker compose run --rm --no-deps \
      -v "$PWD/tests:/opt/app/tests" --entrypoint bash spark-enrich -c \
      'export PYTHONPATH=/opt/app/src:/opt/spark/python:$(ls /opt/spark/python/lib/py4j-*-src.zip) \
       && pip3 install -q --user pytest && python3 -m pytest -q -p no:cacheprovider \
       tests/integration/test_spark_transforms.py'

Run (Linux/CI): pytest -m integration
"""

from __future__ import annotations

import sys
from datetime import datetime, timezone

import pytest

pyspark = pytest.importorskip("pyspark", reason="pyspark not installed (requirements-spark.txt)")

from log_analytics.common.models import LogEvent
from log_analytics.common.parsing import redact, template_id
from log_analytics.streaming.transforms.enrichment import enrich
from log_analytics.streaming.transforms.schema import log_event_schema, parse_kafka_records

pytestmark = [
    pytest.mark.integration,
    pytest.mark.skipif(
        sys.platform == "win32",
        reason="native Windows PySpark workers are unreliable — use the spark container "
        "(TECH-NOTES pitfall #11)",
    ),
]

VALID_PAYLOAD = (
    b'{"timestamp": "2026-07-08T10:00:00Z", "service": "checkout", "level": "warning",'
    b' "message": "user alice@example.com order 12345 took 56.7ms", "host": "ip-10-0-1-11",'
    b' "attributes": {"region": "eu-central-1"}}'
)


@pytest.fixture(scope="module")
def spark():
    from pyspark.sql import SparkSession

    session = (
        SparkSession.builder.master("local[2]")
        .appName("la-transform-tests")
        .config("spark.sql.session.timeZone", "UTC")
        .config("spark.ui.enabled", "false")
        .config("spark.sql.shuffle.partitions", "2")
        .getOrCreate()
    )
    yield session
    session.stop()


def kafka_like(spark, payloads: list[bytes]):
    """A DataFrame shaped like spark.readStream.format('kafka') output."""
    rows = [
        (payload, "logs.raw", 0, offset, datetime(2026, 7, 8, 10, 0, offset, tzinfo=timezone.utc))
        for offset, payload in enumerate(payloads)
    ]
    return spark.createDataFrame(
        rows, "value binary, topic string, partition int, offset long, timestamp timestamp"
    )


def enrich_one(spark, payload: bytes):
    valid, _ = parse_kafka_records(kafka_like(spark, [payload]))
    rows = enrich(valid).collect()
    assert len(rows) == 1
    return rows[0]


def test_parse_splits_valid_and_poison(spark) -> None:
    df = kafka_like(
        spark,
        [
            VALID_PAYLOAD,
            b"{broken json, no closing brace",
            b'{"message": "valid JSON but no service or timestamp"}',
        ],
    )
    valid, dlq = parse_kafka_records(df)

    valid_rows = valid.collect()
    assert len(valid_rows) == 1
    assert valid_rows[0].service == "checkout"
    assert valid_rows[0].offset == 0  # provenance survives the split

    dlq_rows = {row.source_offset: row for row in dlq.collect()}
    assert set(dlq_rows) == {1, 2}
    assert all(row.error == "schema_validation_failed" for row in dlq_rows.values())
    assert dlq_rows[1].payload == "{broken json, no closing brace"  # original preserved
    assert dlq_rows[1].source_topic == "logs.raw"


def test_enrich_normalizes_redacts_and_stamps(spark) -> None:
    row = enrich_one(spark, VALID_PAYLOAD)
    assert row.level == "WARN"  # "warning" alias → canonical
    assert "<email>" in row.message and "alice@example.com" not in row.message
    assert row.env == "dev"  # default filled
    assert row.is_error is False
    assert row.ingest_time is not None
    assert len(row.doc_id) == 40  # sha1 hex
    assert len(row.template_id) == 16


def test_error_levels_flagged(spark) -> None:
    payload = VALID_PAYLOAD.replace(b'"warning"', b'"CRIT"')
    row = enrich_one(spark, payload)
    assert row.level == "FATAL"  # CRIT alias
    assert row.is_error is True


def test_doc_id_is_deterministic_and_content_sensitive(spark) -> None:
    first = enrich_one(spark, VALID_PAYLOAD)
    second = enrich_one(spark, VALID_PAYLOAD)
    assert first.doc_id == second.doc_id  # reprocessing → same _id → no duplicates

    changed = enrich_one(spark, VALID_PAYLOAD.replace(b"order 12345", b"order 99999"))
    assert changed.doc_id != first.doc_id


def test_spark_schema_matches_logevent_contract() -> None:
    """schema.py must stay in sync with common.models.LogEvent (the topic contract)."""
    assert {field.name for field in log_event_schema().fields} == set(LogEvent.model_fields)


def test_template_id_matches_python_implementation(spark) -> None:
    """Same masking regexes through Java regexp_replace and Python re → same hash."""
    raw_message = (
        "user alice@example.com order 12345 took 56.7ms "
        "id=550e8400-e29b-41d4-a716-446655440000 token=deadbeefcafe1234 status 'retry'"
    )
    payload = VALID_PAYLOAD.replace(
        b'"user alice@example.com order 12345 took 56.7ms"',
        b'"' + raw_message.encode() + b'"',
    )
    row = enrich_one(spark, payload)
    assert row.template_id == template_id(redact(raw_message))
