"""Landing files (CSV/JSON dropped to S3) → Bronze Delta.

Batch counterpart to the Kafka stream, for sources that deliver files
(SFTP pushes, partner exports). Same Bronze rules: raw payload + lineage,
no transformation.

Idempotency: a Delta manifest under the artifacts bucket records every file
already loaded; re-runs load only new files, so scheduling this job is safe
regardless of upstream delivery timing.

Run:
    python -m lakehouse.ingestion.batch_file_loader --source s3a://landing/partner-x --table partner_x_orders
"""

from __future__ import annotations

import argparse
from datetime import UTC, datetime

from pyspark.sql import SparkSession
from pyspark.sql import functions as F
from pyspark.sql import types as T

from lakehouse.common.config import LakehouseSettings
from lakehouse.common.logging import get_logger
from lakehouse.common.spark import build_spark_session

JOB_NAME = "batch_file_loader"

MANIFEST_SCHEMA = T.StructType(
    [
        T.StructField("source_file", T.StringType(), False),
        T.StructField("loaded_at", T.TimestampType(), False),
    ]
)


def filter_new_files(candidates: list[str], already_loaded: set[str]) -> list[str]:
    """Pure selection logic — deterministic order for reproducible loads."""
    return sorted(set(candidates) - set(already_loaded))


def _manifest_path(settings: LakehouseSettings, domain: str, table: str) -> str:
    return f"{settings.artifacts_uri}/_manifests/{domain}__{table}"


def _list_source_files(spark: SparkSession, prefix: str) -> list[str]:
    """List regular files under the landing prefix via the Hadoop FileSystem API."""
    jvm = spark.sparkContext._jvm
    conf = spark.sparkContext._jsc.hadoopConfiguration()
    path = jvm.org.apache.hadoop.fs.Path(prefix)
    fs = path.getFileSystem(conf)
    if not fs.exists(path):
        return []
    return [
        status.getPath().toString()
        for status in fs.listStatus(path)
        if status.isFile() and not status.getPath().getName().startswith((".", "_"))
    ]


def _already_loaded(spark: SparkSession, manifest_path: str) -> set[str]:
    from delta.tables import DeltaTable

    if not DeltaTable.isDeltaTable(spark, manifest_path):
        return set()
    rows = spark.read.format("delta").load(manifest_path).select("source_file").collect()
    return {row.source_file for row in rows}


def run(source: str, domain: str, table: str) -> int:
    """Load new landing files into Bronze; returns the number of files loaded."""
    settings = LakehouseSettings.from_env()
    log = get_logger(JOB_NAME, source=source, table=table)
    spark = build_spark_session(JOB_NAME, settings)

    manifest_path = _manifest_path(settings, domain, table)
    new_files = filter_new_files(
        _list_source_files(spark, source), _already_loaded(spark, manifest_path)
    )
    if not new_files:
        log.info("no new files to load")
        return 0

    df = (
        spark.read.text(new_files)
        .withColumnRenamed("value", "payload")
        .withColumn("source_file", F.input_file_name())
        .withColumn("ingested_at", F.current_timestamp())
        .withColumn("ingest_date", F.to_date(F.current_timestamp()))
    )

    target = settings.bronze_path(domain, table)
    df.write.format("delta").mode("append").partitionBy("ingest_date").save(target)

    now = datetime.now(tz=UTC).replace(tzinfo=None)
    manifest_rows = [(file, now) for file in new_files]
    (
        spark.createDataFrame(manifest_rows, MANIFEST_SCHEMA)
        .write.format("delta")
        .mode("append")
        .save(manifest_path)
    )

    log.info(
        "landed batch files to bronze",
        extra={"context": {"target": target, "files": len(new_files)}},
    )
    return len(new_files)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, help="Landing prefix to read")
    parser.add_argument("--table", required=True, help="Bronze table name")
    parser.add_argument("--domain", default="sales")
    args = parser.parse_args()
    run(args.source, args.domain, args.table)


if __name__ == "__main__":
    main()
