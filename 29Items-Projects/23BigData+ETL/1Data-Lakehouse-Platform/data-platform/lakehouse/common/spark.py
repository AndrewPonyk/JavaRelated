"""SparkSession factory — the only place Spark/Delta/S3 wiring is configured.

Local mode (LAKEHOUSE_LOCAL=1): Delta jars come from the pip-installed
`delta-spark` package (plus any `extra_packages`, e.g. the Kafka connector)
and S3A points at MinIO when AWS_ENDPOINT_URL is set.
Cluster mode (EMR/Databricks): the runtime provides Delta, the Kafka
connector, and S3 credentials via the instance role; only behavior flags
are set here.
"""

from __future__ import annotations

import os

from pyspark.sql import SparkSession

from lakehouse.common.config import LakehouseSettings


def build_spark_session(
    app_name: str,
    settings: LakehouseSettings | None = None,
    extra_conf: dict[str, str] | None = None,
    extra_packages: list[str] | None = None,
) -> SparkSession:
    settings = settings or LakehouseSettings.from_env()

    builder = (
        SparkSession.builder.appName(app_name)
        .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
        .config(
            "spark.sql.catalog.spark_catalog",
            "org.apache.spark.sql.delta.catalog.DeltaCatalog",
        )
        .config("spark.sql.session.timeZone", "UTC")
        # Sane defaults; jobs override via extra_conf when profiling says so.
        .config("spark.sql.shuffle.partitions", os.getenv("SPARK_SHUFFLE_PARTITIONS", "64"))
    )

    if settings.s3_endpoint:  # local MinIO
        builder = (
            builder.config("spark.hadoop.fs.s3a.endpoint", settings.s3_endpoint)
            .config("spark.hadoop.fs.s3a.access.key", os.getenv("AWS_ACCESS_KEY_ID", ""))
            .config("spark.hadoop.fs.s3a.secret.key", os.getenv("AWS_SECRET_ACCESS_KEY", ""))
            .config("spark.hadoop.fs.s3a.path.style.access", "true")
            .config("spark.hadoop.fs.s3a.connection.ssl.enabled", "false")
        )
        # Local S3A also needs hadoop-aws on the classpath; pass it via
        # extra_packages (EMR ships it, so it stays out of cluster configs):
        #   extra_packages=["org.apache.hadoop:hadoop-aws:3.3.4"]

    if extra_conf:
        for key, value in extra_conf.items():
            builder = builder.config(key, value)

    if settings.local_mode:
        builder = builder.master(os.getenv("SPARK_MASTER", "local[*]"))
        # Resolves io.delta jars matching the pip-installed delta-spark version,
        # plus any job-specific connectors (Kafka, S3A).
        from delta import configure_spark_with_delta_pip

        return configure_spark_with_delta_pip(
            builder, extra_packages=extra_packages or []
        ).getOrCreate()

    return builder.getOrCreate()
