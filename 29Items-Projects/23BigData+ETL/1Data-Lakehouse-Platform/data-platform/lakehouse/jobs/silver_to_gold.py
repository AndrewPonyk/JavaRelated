"""Silver → Gold: business-level aggregates owned by Spark.

Gold has two producers by design (see ARCHITECTURE.md): dbt-on-Trino builds
analyst marts; Spark builds aggregates that need heavy compute or feed ML.
This job maintains gold.sales.order_daily_stats.

Run:
    python -m lakehouse.jobs.silver_to_gold --run-date 2026-07-01
"""

from __future__ import annotations

import argparse

from pyspark.sql import functions as F

from lakehouse.common.catalog import register_dataset_version
from lakehouse.common.config import LakehouseSettings
from lakehouse.common.logging import get_logger
from lakehouse.common.metrics import record_job_metrics
from lakehouse.common.spark import build_spark_session

JOB_NAME = "silver_to_gold"


def run(run_date: str) -> None:
    settings = LakehouseSettings.from_env()
    log = get_logger(JOB_NAME, run_date=run_date)
    spark = build_spark_session(JOB_NAME, settings)

    orders = (
        spark.read.format("delta")
        .load(settings.silver_path("sales", "orders"))
        .filter(F.col("event_date") == F.lit(run_date))
    )

    daily = orders.groupBy("event_date", "currency").agg(
        F.count("*").alias("order_count"),
        F.sum("amount").alias("gross_amount"),
        F.countDistinct("customer_id").alias("unique_customers"),
    )
    daily.persist()
    row_count = daily.count()

    # Partition-scoped overwrite: idempotent per run_date, and disjoint partitions
    # keep concurrent backfills free of Delta write conflicts.
    target = settings.gold_path("sales", "order_daily_stats")
    (
        daily.write.format("delta")
        .mode("overwrite")
        .option("replaceWhere", f"event_date = '{run_date}'")
        .partitionBy("event_date")
        .save(target)
    )
    log.info("gold aggregate written", extra={"context": {"rows": row_count}})

    record_job_metrics(
        spark,
        settings,
        job=JOB_NAME,
        subject="gold/sales/order_daily_stats",
        run_date=run_date,
        metrics={"rows_written": row_count},
    )
    register_dataset_version(
        settings,
        name="sales.order_daily_stats",
        layer="gold",
        s3_path=target,
        schema={field.name: field.dataType.simpleString() for field in daily.schema.fields},
        row_count=row_count,
        description="Daily order aggregates per currency (owned by silver_to_gold).",
    )
    daily.unpersist()


def main() -> None:
    from lakehouse.common.cli import run_date_arg

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-date", required=True, type=run_date_arg)
    run(parser.parse_args().run_date)


if __name__ == "__main__":
    main()
