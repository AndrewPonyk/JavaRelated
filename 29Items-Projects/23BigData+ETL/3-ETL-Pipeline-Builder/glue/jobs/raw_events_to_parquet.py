"""AWS Glue 4.0 PySpark job: normalize raw JSON events into partitioned Parquet.

Contract
    reads   s3://<lake_bucket>/raw/events/dt=<ds>/*.json.gz     (JSON lines)
    writes  s3://<lake_bucket>/staged/events/dt=<ds>/            (snappy Parquet, deduped)
    writes  s3://<lake_bucket>/quarantine/events/dt=<ds>/        (contract violations + reason)

Rows violating the envelope contract (missing event_id/event_type, unparseable
ts_ms) are quarantined with an error reason — never silently dropped, never
allowed into the staged zone. Quarantine depth feeds a CloudWatch metric for
volume-anomaly alarms.

The transformation is a pure function (`transform`) unit-tested without Glue
(glue/tests/, marker `glue` — run in a Linux container / CI).

Job args (pinned by infrastructure/modules/data_lake):
    --lake_bucket   S3 bucket holding the lake zones
    --ds            partition date YYYY-MM-DD
"""

from __future__ import annotations

import sys

REQUIRED_COLUMNS = ("event_id", "event_type", "ts_ms")
CATALOG_DATABASE_ARG = "catalog_database"  # optional job arg


def transform(df):
    """Split a raw events DataFrame into (staged, quarantined).

    staged:      typed, deduped (latest per event_id wins — at-least-once
                 upstream means duplicates are expected)
    quarantined: original rows + `_error` reason for replay after a fix
    """
    from pyspark.sql import Window
    from pyspark.sql import functions as F

    checked = df.withColumn(
        "_error",
        F.when(F.col("event_id").isNull(), F.lit("missing event_id"))
        .when(F.col("event_type").isNull(), F.lit("missing event_type"))
        .when(F.col("ts_ms").cast("long").isNull(), F.lit("ts_ms not a number"))
        .otherwise(F.lit(None)),
    )

    quarantined = checked.filter(F.col("_error").isNotNull())

    latest_first = Window.partitionBy("event_id").orderBy(F.col("ts_ms").desc())
    staged = (
        checked.filter(F.col("_error").isNull())
        .drop("_error")
        .withColumn("ts_ms", F.col("ts_ms").cast("long"))
        .withColumn("amount", F.coalesce(F.col("amount").cast("double"), F.lit(0.0)))
        .withColumn("event_at", F.timestamp_millis(F.col("ts_ms")))
        .withColumn("_rn", F.row_number().over(latest_first))
        .filter(F.col("_rn") == 1)
        .drop("_rn")
    )
    return staged, quarantined


def _emit_row_count_metrics(ds: str, staged_count: int, quarantined_count: int) -> None:
    """CloudWatch volume metrics; alarmed on quarantine spikes. Best-effort —
    a metrics hiccup must not fail a successful load."""
    try:
        import boto3

        boto3.client("cloudwatch").put_metric_data(
            Namespace="EtlPipelineBuilder/Glue",
            MetricData=[
                {
                    "MetricName": name,
                    "Value": value,
                    "Unit": "Count",
                    "Dimensions": [{"Name": "Partition", "Value": ds}],
                }
                for name, value in [
                    ("StagedEvents", staged_count),
                    ("QuarantinedEvents", quarantined_count),
                ]
            ],
        )
    except Exception as exc:  # noqa: BLE001 — observability is best-effort here
        print(f"warn: could not emit CloudWatch metrics: {exc}")


def _register_partition(database: str, lake_bucket: str, ds: str) -> None:
    """Register the staged partition in the Glue Data Catalog (idempotent)."""
    try:
        import boto3

        glue = boto3.client("glue")
        glue.create_partition(
            DatabaseName=database,
            TableName="staged_events",
            PartitionInput={
                "Values": [ds],
                "StorageDescriptor": {
                    "Location": f"s3://{lake_bucket}/staged/events/dt={ds}/",
                    "InputFormat": (
                        "org.apache.hadoop.hive.ql.io.parquet.MapredParquetInputFormat"
                    ),
                    "OutputFormat": (
                        "org.apache.hadoop.hive.ql.io.parquet.MapredParquetOutputFormat"
                    ),
                    "SerdeInfo": {
                        "SerializationLibrary": (
                            "org.apache.hadoop.hive.ql.io.parquet.serde.ParquetHiveSerDe"
                        )
                    },
                },
            },
        )
    except Exception as exc:  # AlreadyExistsException on rerun is fine
        print(f"partition registration: {exc}")


def main() -> None:
    from awsglue.context import GlueContext
    from awsglue.job import Job
    from awsglue.utils import getResolvedOptions
    from pyspark.context import SparkContext

    args = getResolvedOptions(sys.argv, ["JOB_NAME", "lake_bucket", "ds"])
    glue_context = GlueContext(SparkContext.getOrCreate())
    spark = glue_context.spark_session
    job = Job(glue_context)
    job.init(args["JOB_NAME"], args)

    lake_bucket, ds = args["lake_bucket"], args["ds"]
    source_path = f"s3://{lake_bucket}/raw/events/dt={ds}/"
    staged_path = f"s3://{lake_bucket}/staged/events/dt={ds}/"
    quarantine_path = f"s3://{lake_bucket}/quarantine/events/dt={ds}/"

    df = spark.read.json(source_path)
    staged, quarantined = transform(df)

    staged_count = staged.count()
    quarantined_count = quarantined.count()

    staged.write.mode("overwrite").parquet(staged_path, compression="snappy")
    if quarantined_count:
        quarantined.write.mode("overwrite").json(quarantine_path)

    print(f"staged={staged_count} quarantined={quarantined_count} partition={ds}")
    _emit_row_count_metrics(ds, staged_count, quarantined_count)
    if args.get(CATALOG_DATABASE_ARG):
        _register_partition(args[CATALOG_DATABASE_ARG], lake_bucket, ds)

    job.commit()


if __name__ == "__main__":
    main()
