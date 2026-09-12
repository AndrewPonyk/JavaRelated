"""Delta table maintenance: compaction, Z-ORDER, VACUUM, checkpoint cleanup.

Streaming and frequent MERGEs create small files; this job keeps read
performance and storage costs in check. Scheduled by the medallion DAG
(daily compaction; VACUUM respects the 7-day time-travel window).

Also records file-count/size metrics per table (small-file trend alerting)
and removes streaming checkpoints that have been inactive for 30+ days —
an inactive checkpoint belongs to a retired stream and only costs storage.

Run:
    python -m lakehouse.jobs.table_maintenance --retention-hours 168
"""

from __future__ import annotations

import argparse
import time
from datetime import UTC, datetime

from pyspark.sql import SparkSession

from lakehouse.common.config import LakehouseSettings
from lakehouse.common.logging import get_logger
from lakehouse.common.metrics import record_job_metrics
from lakehouse.common.spark import build_spark_session

JOB_NAME = "table_maintenance"

# Table registry for maintenance. The governance catalog is the human-facing
# registry; this list is the pipeline-owned source of truth for which tables
# the platform itself maintains (kept in code so changes go through review).
MANAGED_TABLES: list[tuple[str, str, str | None]] = [
    # (layer, domain/table, zorder column or None)
    ("bronze", "sales/orders", None),
    ("silver", "sales/orders", "customer_id"),
    ("gold", "sales/order_daily_stats", None),
    ("gold", "ml/customer_order_features", "customer_id"),
]

STALE_CHECKPOINT_DAYS = 30


def _table_exists(spark: SparkSession, path: str) -> bool:
    from delta.tables import DeltaTable

    return DeltaTable.isDeltaTable(spark, path)


def table_file_stats(spark: SparkSession, path: str) -> dict[str, int]:
    """numFiles/sizeInBytes from DESCRIBE DETAIL — the small-file health signal."""
    detail = spark.sql(f"DESCRIBE DETAIL delta.`{path}`").collect()[0]
    return {"num_files": int(detail["numFiles"]), "size_bytes": int(detail["sizeInBytes"])}


def cleanup_stale_checkpoints(
    spark: SparkSession, settings: LakehouseSettings, max_age_days: int = STALE_CHECKPOINT_DAYS
) -> int:
    """Delete checkpoint directories not modified for `max_age_days`.

    An actively-committing stream touches its checkpoint every batch, so age
    is a safe inactivity signal at this threshold.
    """
    jvm = spark.sparkContext._jvm
    conf = spark.sparkContext._jsc.hadoopConfiguration()
    root = jvm.org.apache.hadoop.fs.Path(f"{settings.artifacts_uri}/_checkpoints")
    fs = root.getFileSystem(conf)
    if not fs.exists(root):
        return 0

    cutoff_ms = int((time.time() - max_age_days * 86400) * 1000)
    removed = 0
    for status in fs.listStatus(root):
        if status.getModificationTime() < cutoff_ms:
            fs.delete(status.getPath(), True)
            removed += 1
    return removed


def run(retention_hours: int) -> None:
    settings = LakehouseSettings.from_env()
    log = get_logger(JOB_NAME)
    spark = build_spark_session(JOB_NAME, settings)
    run_date = datetime.now(tz=UTC).date().isoformat()

    for layer, table, zorder in MANAGED_TABLES:
        path = settings.layer_path(layer, *table.split("/"))
        if not _table_exists(spark, path):
            log.info("skipping table that does not exist yet", extra={"context": {"table": path}})
            continue
        log.info("maintaining table", extra={"context": {"table": path}})

        optimize = f"OPTIMIZE delta.`{path}`"
        if zorder:
            optimize += f" ZORDER BY ({zorder})"
        spark.sql(optimize)

        # Never lower retention below the time-travel/streaming window (7 days).
        spark.sql(f"VACUUM delta.`{path}` RETAIN {retention_hours} HOURS")

        stats = table_file_stats(spark, path)
        record_job_metrics(
            spark,
            settings,
            job=JOB_NAME,
            subject=f"{layer}/{table}",
            run_date=run_date,
            metrics=stats,
        )

    removed = cleanup_stale_checkpoints(spark, settings)
    log.info("checkpoint cleanup complete", extra={"context": {"removed": removed}})


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--retention-hours", type=int, default=168)
    args = parser.parse_args()
    if args.retention_hours < 168:
        raise SystemExit("retention below 168h breaks time travel and streaming readers")
    run(args.retention_hours)


if __name__ == "__main__":
    main()
