"""ML feature pipeline: per-customer order behavior features.

Writes a snapshotted feature table to Gold, partitioned by feature_date.
Training jobs join labels to the snapshot where feature_date <= label_date,
which is what guarantees point-in-time correctness (no target leakage).

After writing, the snapshot is drift-checked against the previous one: large
shifts in mean spend/order counts are logged as warnings (signal for the ML
team that retraining data changed character), and the snapshot is registered
in the governance catalog.

Run:
    python -m lakehouse.features.customer_order_features --feature-date 2026-07-01
"""

from __future__ import annotations

import argparse

from pyspark.sql import DataFrame, Window
from pyspark.sql import functions as F

from lakehouse.common.catalog import register_dataset_version
from lakehouse.common.config import LakehouseSettings
from lakehouse.common.logging import get_logger
from lakehouse.common.metrics import record_job_metrics
from lakehouse.common.spark import build_spark_session

JOB_NAME = "customer_order_features"

DRIFT_COLUMNS = ("spend_30d", "orders_30d")
DRIFT_RATIO_BOUNDS = (0.5, 2.0)  # mean shift outside this range → warn


def compute_features(orders: DataFrame, feature_date: str) -> DataFrame:
    """Rolling-window aggregates as of feature_date (exclusive of future data)."""
    asof = orders.filter(F.col("event_date") <= F.lit(feature_date))

    def window_agg(days: int) -> DataFrame:
        cutoff = F.date_sub(F.lit(feature_date).cast("date"), days)
        return (
            asof.filter(F.col("event_date") > cutoff)
            .groupBy("customer_id")
            .agg(
                F.count("*").alias(f"orders_{days}d"),
                F.sum("amount").alias(f"spend_{days}d"),
            )
        )

    base = asof.groupBy("customer_id").agg(
        F.max("event_date").alias("last_order_date"),
        F.count("*").alias("orders_lifetime"),
    )

    # Categorical: most frequent currency (ties broken alphabetically for determinism).
    currency_counts = asof.groupBy("customer_id", "currency").count()
    preference = Window.partitionBy("customer_id").orderBy(
        F.col("count").desc(), F.col("currency").asc()
    )
    favorite_currency = (
        currency_counts.withColumn("_rank", F.row_number().over(preference))
        .filter(F.col("_rank") == 1)
        .select("customer_id", F.col("currency").alias("favorite_currency"))
    )

    return (
        base.join(window_agg(7), "customer_id", "left")
        .join(window_agg(30), "customer_id", "left")
        .join(favorite_currency, "customer_id", "left")
        .na.fill(0, ["orders_7d", "spend_7d", "orders_30d", "spend_30d"])
        .withColumn("feature_date", F.lit(feature_date).cast("date"))
    )


def snapshot_drift(current: DataFrame, previous: DataFrame) -> dict[str, float]:
    """Mean-shift ratio per drift column (current/previous). 1.0 == no drift.

    Deliberately simple: cheap to compute on every snapshot and catches the
    catastrophic cases (pipeline bug halves spend, duplicate ingestion doubles
    orders). Distribution-level drift belongs to the training pipeline.
    """
    cur = current.agg(*[F.avg(c).alias(c) for c in DRIFT_COLUMNS]).collect()[0]
    prev = previous.agg(*[F.avg(c).alias(c) for c in DRIFT_COLUMNS]).collect()[0]

    ratios: dict[str, float] = {}
    for column in DRIFT_COLUMNS:
        current_mean = float(cur[column] or 0.0)
        previous_mean = float(prev[column] or 0.0)
        if previous_mean == 0.0:
            ratios[column] = 1.0 if current_mean == 0.0 else float("inf")
        else:
            ratios[column] = current_mean / previous_mean
    return ratios


def run(feature_date: str) -> None:
    settings = LakehouseSettings.from_env()
    log = get_logger(JOB_NAME, feature_date=feature_date)
    spark = build_spark_session(JOB_NAME, settings)

    orders = spark.read.format("delta").load(settings.silver_path("sales", "orders"))
    features = compute_features(orders, feature_date)
    features.persist()
    row_count = features.count()

    target = settings.gold_path("ml", "customer_order_features")
    from delta.tables import DeltaTable

    previous: DataFrame | None = None
    if DeltaTable.isDeltaTable(spark, target):
        existing = spark.read.format("delta").load(target)
        latest_prior = (
            existing.filter(F.col("feature_date") < F.lit(feature_date))
            .agg(F.max("feature_date"))
            .collect()[0][0]
        )
        if latest_prior is not None:
            previous = existing.filter(F.col("feature_date") == F.lit(latest_prior))

    (
        features.write.format("delta")
        .mode("overwrite")
        .option("replaceWhere", f"feature_date = '{feature_date}'")
        .partitionBy("feature_date")
        .save(target)
    )
    log.info("feature snapshot written", extra={"context": {"rows": row_count}})

    if previous is not None:
        ratios = snapshot_drift(features, previous)
        low, high = DRIFT_RATIO_BOUNDS
        for column, ratio in ratios.items():
            drifted = not (low <= ratio <= high)
            (log.warning if drifted else log.info)(
                "feature drift check",
                extra={"context": {"column": column, "mean_ratio": round(ratio, 3)}},
            )

    record_job_metrics(
        spark,
        settings,
        job=JOB_NAME,
        subject="gold/ml/customer_order_features",
        run_date=feature_date,
        metrics={"rows_written": row_count},
    )
    register_dataset_version(
        settings,
        name="ml.customer_order_features",
        layer="gold",
        s3_path=target,
        schema={field.name: field.dataType.simpleString() for field in features.schema.fields},
        row_count=row_count,
        description="Point-in-time customer order features for ML training.",
    )
    features.unpersist()


def main() -> None:
    from lakehouse.common.cli import run_date_arg

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--feature-date", required=True, type=run_date_arg)
    run(parser.parse_args().feature_date)


if __name__ == "__main__":
    main()
