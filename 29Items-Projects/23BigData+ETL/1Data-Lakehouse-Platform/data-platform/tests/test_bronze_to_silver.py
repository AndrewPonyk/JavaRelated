"""Transformation tests for the Bronze→Silver refine step (pure function, no I/O)."""

from __future__ import annotations

import datetime
import json

import pytest

pytestmark = pytest.mark.spark

INGESTED_AT = datetime.datetime(2026, 7, 1, 12, 0, 0)


def _bronze_row(payload: dict, offset: int) -> dict:
    return {"payload": json.dumps(payload), "kafka_offset": offset, "ingested_at": INGESTED_AT}


GOOD = {
    "order_id": "o-1",
    "customer_id": "c-1",
    "order_ts": "2026-07-01T10:00:00Z",
    "status": "paid",
    "amount": 42.5,
    "currency": "EUR",
}


def test_valid_rows_pass_and_get_event_date(spark):
    from lakehouse.jobs.bronze_to_silver import refine_orders

    bronze = spark.createDataFrame([_bronze_row(GOOD, 1)])
    clean, rejected = refine_orders(bronze)

    assert rejected.count() == 0
    row = clean.collect()[0]
    assert row.order_id == "o-1"
    assert str(row.event_date) == "2026-07-01"


def test_duplicates_keep_latest_event(spark):
    from lakehouse.jobs.bronze_to_silver import refine_orders

    older = {**GOOD, "status": "created", "order_ts": "2026-07-01T09:00:00Z"}
    bronze = spark.createDataFrame([_bronze_row(older, 1), _bronze_row(GOOD, 2)])
    clean, _ = refine_orders(bronze)

    rows = clean.collect()
    assert len(rows) == 1
    assert rows[0].status == "paid"  # the later event wins


def test_invalid_rows_are_quarantined_with_reason(spark):
    from lakehouse.jobs.bronze_to_silver import refine_orders

    negative = {**GOOD, "order_id": "o-2", "amount": -5}
    missing_key = {**GOOD, "order_id": None}
    bronze = spark.createDataFrame(
        [_bronze_row(GOOD, 1), _bronze_row(negative, 2), _bronze_row(missing_key, 3)]
    )
    clean, rejected = refine_orders(bronze)

    assert clean.count() == 1
    reasons = {r.reject_reason for r in rejected.collect()}
    assert reasons == {"invalid amount", "missing order_id"}


def test_null_status_and_currency_are_quarantined(spark):
    """Regression: `~isin(...)` is NULL (not true) for NULL inputs, so rows with
    missing status/currency used to slip through into Silver."""
    from lakehouse.jobs.bronze_to_silver import refine_orders

    null_status = {**GOOD, "order_id": "o-3", "status": None}
    weird_status = {**GOOD, "order_id": "o-4", "status": "teleported"}
    null_currency = {**GOOD, "order_id": "o-5", "currency": None}
    bronze = spark.createDataFrame(
        [
            _bronze_row(GOOD, 1),
            _bronze_row(null_status, 2),
            _bronze_row(weird_status, 3),
            _bronze_row(null_currency, 4),
        ]
    )
    clean, rejected = refine_orders(bronze)

    assert clean.count() == 1  # only GOOD survives
    by_id = {r.order_id: r.reject_reason for r in rejected.collect()}
    assert by_id == {
        "o-3": "missing/unknown status",
        "o-4": "missing/unknown status",
        "o-5": "missing currency",
    }
