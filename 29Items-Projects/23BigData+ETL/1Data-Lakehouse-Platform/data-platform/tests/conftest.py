"""Shared fixtures. Spark tests are opt-in: pytest -m spark."""

from __future__ import annotations

import os
import sys

import pytest


@pytest.fixture(scope="session")
def spark():
    """Small local SparkSession with Delta enabled (session-scoped: JVM startup is slow)."""
    pytest.importorskip("pyspark")
    # Workers must use the same interpreter as the driver (on Windows there is
    # no `python3` executable, which is Spark's default worker command).
    os.environ.setdefault("PYSPARK_PYTHON", sys.executable)
    os.environ.setdefault("PYSPARK_DRIVER_PYTHON", sys.executable)

    from lakehouse.common.config import LakehouseSettings
    from lakehouse.common.spark import build_spark_session

    settings = LakehouseSettings(
        bronze_uri="/tmp/lake-test/bronze",
        silver_uri="/tmp/lake-test/silver",
        gold_uri="/tmp/lake-test/gold",
        artifacts_uri="/tmp/lake-test/artifacts",
        kafka_bootstrap_servers="unused:9092",
        orders_topic="orders.v1",
        dlq_topic="orders.v1.dlq",
        s3_endpoint=None,
        local_mode=True,
    )
    session = build_spark_session(
        "lakehouse-tests",
        settings,
        extra_conf={"spark.sql.shuffle.partitions": "2", "spark.ui.enabled": "false"},
    )
    yield session
    session.stop()
