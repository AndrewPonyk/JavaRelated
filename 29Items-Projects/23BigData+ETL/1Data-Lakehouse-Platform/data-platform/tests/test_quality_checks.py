"""Tests for the data-quality gate primitives."""

from __future__ import annotations

import pytest

pytestmark = pytest.mark.spark


def test_run_checks_counts_violations_per_check(spark):
    from pyspark.sql import functions as F

    from lakehouse.quality.checks import Check, run_checks

    df = spark.createDataFrame(
        [("o-1", 10.0), ("o-2", -1.0), (None, 5.0)],
        ["order_id", "amount"],
    )
    results = run_checks(
        df,
        [
            Check("order_id_not_null", lambda: F.col("order_id").isNull()),
            Check("amount_non_negative", lambda: F.col("amount") < 0),
            Check("never_fires", lambda: F.lit(False), severity="warn"),
        ],
    )

    by_name = {r.name: r for r in results}
    assert by_name["order_id_not_null"].violations == 1
    assert by_name["amount_non_negative"].violations == 1
    assert by_name["never_fires"].passed
    assert by_name["never_fires"].severity == "warn"
