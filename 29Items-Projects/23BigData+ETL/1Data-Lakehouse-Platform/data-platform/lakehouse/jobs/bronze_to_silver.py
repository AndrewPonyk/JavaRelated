"""Bronze → Silver promotion for orders.

Parse raw payloads against the versioned contract, reject invalid rows to
quarantine (with reasons), dedupe on the business key, and MERGE into Silver.
Idempotent: re-running any --run-date produces the same Silver state, which is
what makes backfills and Airflow retries safe.

Each run records rows-in/clean/quarantined metrics and registers the resulting
schema + row count in the governance catalog.

Run:
    python -m lakehouse.jobs.bronze_to_silver --run-date 2026-07-01
"""

from __future__ import annotations

import argparse

from pyspark.sql import DataFrame, SparkSession, Window
from pyspark.sql import functions as F
from pyspark.sql import types as T

from lakehouse.common.catalog import register_dataset_version
from lakehouse.common.config import LakehouseSettings
from lakehouse.common.logging import get_logger
from lakehouse.common.metrics import record_job_metrics
from lakehouse.common.spark import build_spark_session

JOB_NAME = "bronze_to_silver"

# Contract for topic orders.v1. Additive-only evolution: a breaking change means
# orders.v2 (see docs/ARCHITECTURE.md §2.2 — interfaces are versioned contracts).
ORDER_SCHEMA = T.StructType(
    [
        T.StructField("order_id", T.StringType(), nullable=False),
        T.StructField("customer_id", T.StringType(), nullable=False),
        T.StructField("order_ts", T.TimestampType(), nullable=False),
        T.StructField("status", T.StringType(), nullable=True),
        T.StructField("amount", T.DoubleType(), nullable=True),
        T.StructField("currency", T.StringType(), nullable=True),
    ]
)

VALID_STATUSES = ("created", "paid", "shipped", "cancelled")


def refine_orders(bronze: DataFrame) -> tuple[DataFrame, DataFrame]:
    """Pure transformation: bronze rows -> (clean, rejected).

    Kept free of I/O so it is directly unit-testable
    (see data-platform/tests/test_bronze_to_silver.py).
    """
    parsed = bronze.withColumn("parsed", F.from_json(F.col("payload"), ORDER_SCHEMA)).select(
        "parsed.*", "kafka_offset", "ingested_at"
    )

    # NULL-safety: `~isin(...)` evaluates to NULL (not true) for NULL inputs,
    # so nullable columns need explicit isNull() terms or bad rows slip through.
    reason = (
        F.when(F.col("order_id").isNull(), F.lit("missing order_id"))
        .when(F.col("customer_id").isNull(), F.lit("missing customer_id"))
        .when(F.col("order_ts").isNull(), F.lit("missing/unparsable order_ts"))
        .when(F.col("amount").isNull() | (F.col("amount") < 0), F.lit("invalid amount"))
        .when(
            F.col("status").isNull() | ~F.col("status").isin(*VALID_STATUSES),
            F.lit("missing/unknown status"),
        )
        .when(F.col("currency").isNull(), F.lit("missing currency"))
    )
    checked = parsed.withColumn("reject_reason", reason)

    rejected = checked.filter(F.col("reject_reason").isNotNull())

    # Dedupe at-least-once delivery: keep the latest event per order_id.
    latest_first = Window.partitionBy("order_id").orderBy(
        F.col("order_ts").desc(), F.col("kafka_offset").desc()
    )
    clean = (
        checked.filter(F.col("reject_reason").isNull())
        .withColumn("_rn", F.row_number().over(latest_first))
        .filter(F.col("_rn") == 1)
        .drop("_rn", "reject_reason", "kafka_offset")
        .withColumn("event_date", F.to_date("order_ts"))  # Silver partition column
    )
    return clean, rejected


def merge_into_silver(spark: SparkSession, clean: DataFrame, target: str) -> None:
    """Upsert by business key — the idempotency anchor of the whole pipeline."""
    from delta.tables import DeltaTable

    if not DeltaTable.isDeltaTable(spark, target):
        clean.write.format("delta").partitionBy("event_date").save(target)
        return

    (
        DeltaTable.forPath(spark, target)
        .alias("t")
        .merge(clean.alias("s"), "t.order_id = s.order_id")
        .whenMatchedUpdateAll()
        .whenNotMatchedInsertAll()
        .execute()
    )


def run(run_date: str) -> None:
    settings = LakehouseSettings.from_env()
    log = get_logger(JOB_NAME, run_date=run_date)
    spark = build_spark_session(JOB_NAME, settings)

    bronze = (
        spark.read.format("delta")
        .load(settings.bronze_path("sales", "orders"))
        .filter(F.col("ingest_date") == F.lit(run_date))
    )
    bronze.persist()
    rows_in = bronze.count()

    clean, rejected = refine_orders(bronze)
    clean.persist()
    clean_count = clean.count()
    rejected_count = rejected.count()

    if rejected_count:
        (
            rejected.withColumn("run_date", F.lit(run_date))
            .write.format("delta")
            .mode("append")
            .save(settings.quarantine_path("sales", "orders"))
        )
        log.warning("rows quarantined", extra={"context": {"rejected": rejected_count}})

    target = settings.silver_path("sales", "orders")
    merge_into_silver(spark, clean, target)
    silver_total = spark.read.format("delta").load(target).count()
    log.info(
        "silver merge complete",
        extra={"context": {"clean_rows": clean_count, "silver_total": silver_total}},
    )

    record_job_metrics(
        spark,
        settings,
        job=JOB_NAME,
        subject="silver/sales/orders",
        run_date=run_date,
        metrics={
            "rows_in": rows_in,
            "rows_clean": clean_count,
            "rows_quarantined": rejected_count,
            "silver_total": silver_total,
        },
    )
    register_dataset_version(
        settings,
        name="sales.orders",
        layer="silver",
        s3_path=target,
        schema={field.name: field.dataType.simpleString() for field in clean.schema.fields},
        row_count=silver_total,
        description="Deduplicated, validated order events (owned by bronze_to_silver).",
    )
    bronze.unpersist()
    clean.unpersist()


def main() -> None:
    from lakehouse.common.cli import run_date_arg

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--run-date",
        required=True,
        type=run_date_arg,
        help="Bronze ingest_date to promote (YYYY-MM-DD)",
    )
    run(parser.parse_args().run_date)


if __name__ == "__main__":
    main()
