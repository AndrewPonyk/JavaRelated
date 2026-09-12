"""Operational metrics and DQ results, persisted as Delta tables under the
artifacts bucket. These power freshness dashboards and DQ pass-rate alerting;
each write also emits a structured log line for CloudWatch metric filters."""

from __future__ import annotations

from datetime import UTC, datetime
from typing import TYPE_CHECKING

from pyspark.sql import SparkSession
from pyspark.sql import types as T

from lakehouse.common.config import LakehouseSettings
from lakehouse.common.logging import get_logger

if TYPE_CHECKING:  # avoid a runtime import cycle with quality.checks
    from lakehouse.quality.checks import CheckResult

JOB_METRICS_SCHEMA = T.StructType(
    [
        T.StructField("job", T.StringType(), False),
        T.StructField("subject", T.StringType(), False),
        T.StructField("run_date", T.StringType(), False),
        T.StructField("metric", T.StringType(), False),
        T.StructField("value", T.LongType(), False),
        T.StructField("recorded_at", T.TimestampType(), False),
    ]
)

DQ_RESULTS_SCHEMA = T.StructType(
    [
        T.StructField("table", T.StringType(), False),
        T.StructField("run_date", T.StringType(), False),
        T.StructField("check", T.StringType(), False),
        T.StructField("severity", T.StringType(), False),
        T.StructField("violations", T.LongType(), False),
        T.StructField("passed", T.BooleanType(), False),
        T.StructField("recorded_at", T.TimestampType(), False),
    ]
)


def job_metrics_path(settings: LakehouseSettings) -> str:
    return f"{settings.artifacts_uri}/_metrics/job_runs"


def dq_results_path(settings: LakehouseSettings) -> str:
    return f"{settings.artifacts_uri}/_audit/dq_results"


def record_job_metrics(
    spark: SparkSession,
    settings: LakehouseSettings,
    job: str,
    subject: str,
    run_date: str,
    metrics: dict[str, int],
) -> None:
    log = get_logger("metrics", job=job, subject=subject, run_date=run_date)
    log.info("job metrics", extra={"context": dict(metrics)})
    now = datetime.now(tz=UTC).replace(tzinfo=None)  # Spark timestamps are TZ-naive UTC
    rows = [(job, subject, run_date, name, int(value), now) for name, value in metrics.items()]
    (
        spark.createDataFrame(rows, JOB_METRICS_SCHEMA)
        .write.format("delta")
        .mode("append")
        .save(job_metrics_path(settings))
    )


def record_dq_results(
    spark: SparkSession,
    settings: LakehouseSettings,
    table: str,
    run_date: str,
    results: list[CheckResult],
) -> None:
    log = get_logger("dq", table=table, run_date=run_date)
    log.info(
        "dq results",
        extra={"context": {r.name: r.violations for r in results}},
    )
    now = datetime.now(tz=UTC).replace(tzinfo=None)
    rows = [
        (table, run_date, r.name, r.severity, int(r.violations), r.passed, now) for r in results
    ]
    (
        spark.createDataFrame(rows, DQ_RESULTS_SCHEMA)
        .write.format("delta")
        .mode("append")
        .save(dq_results_path(settings))
    )
