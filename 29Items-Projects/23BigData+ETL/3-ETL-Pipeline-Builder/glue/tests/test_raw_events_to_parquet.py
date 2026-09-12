"""Unit tests for the pure transform() of the Glue job.

Marked `glue`: requires a local Spark runtime, which is unreliable on Windows —
run inside a Linux container or CI (`pytest -m glue -o addopts=""`). Excluded
by default via pyproject addopts.
"""

import pytest

pytestmark = pytest.mark.glue

pyspark = pytest.importorskip("pyspark", reason="pyspark not installed; Glue tests run in CI")

from jobs.raw_events_to_parquet import transform  # noqa: E402


@pytest.fixture(scope="session")
def spark():
    from pyspark.sql import SparkSession

    session = SparkSession.builder.master("local[1]").appName("glue-unit-tests").getOrCreate()
    yield session
    session.stop()


def test_dedupes_on_event_id_keeping_latest(spark):
    rows = [
        {"event_id": "e1", "event_type": "order_placed", "ts_ms": 1000, "amount": "10.0"},
        {"event_id": "e1", "event_type": "order_placed", "ts_ms": 2000, "amount": "99.0"},
        {"event_id": "e2", "event_type": "checkout_failed", "ts_ms": 1500, "amount": None},
    ]
    staged, quarantined = transform(spark.createDataFrame(rows))

    out = staged.collect()
    assert quarantined.count() == 0
    assert len(out) == 2
    e1 = next(r for r in out if r.event_id == "e1")
    assert e1.ts_ms == 2000
    assert e1.amount == 99.0


def test_contract_violations_are_quarantined_with_reason(spark):
    rows = [
        {"event_id": None, "event_type": "order_placed", "ts_ms": 1000, "amount": "1"},
        {
            "event_id": "e-bad-ts",
            "event_type": "order_placed",
            "ts_ms": "not-a-number",
            "amount": "1",
        },
        {"event_id": "ok", "event_type": "order_placed", "ts_ms": 1000, "amount": "1"},
    ]
    staged, quarantined = transform(spark.createDataFrame(rows))

    assert [r.event_id for r in staged.collect()] == ["ok"]
    bad = {r.event_id: r._error for r in quarantined.collect()}
    assert bad[None] == "missing event_id"
    assert bad["e-bad-ts"] == "ts_ms not a number"
