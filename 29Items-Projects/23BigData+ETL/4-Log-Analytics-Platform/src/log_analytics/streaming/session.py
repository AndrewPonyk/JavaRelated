"""SparkSession factory and shared Kafka connection options.

The Kafka connector jar is NOT bundled with pyspark — provide it at submit time:
    spark-submit --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.1 …
(`scripts/submit_spark_job.sh` does this; EMR Serverless ships it built-in.)
"""

from __future__ import annotations

import os

from pyspark.sql import SparkSession

_JAAS_MODULES = {
    "PLAIN": "org.apache.kafka.common.security.plain.PlainLoginModule",
    "SCRAM-SHA-256": "org.apache.kafka.common.security.scram.ScramLoginModule",
    "SCRAM-SHA-512": "org.apache.kafka.common.security.scram.ScramLoginModule",
}


def build_spark_session(app_name: str, extra_conf: dict[str, str] | None = None) -> SparkSession:
    builder = (
        SparkSession.builder.appName(app_name)
        .config("spark.sql.session.timeZone", "UTC")
        .config("spark.sql.adaptive.enabled", "true")
        .config("spark.sql.shuffle.partitions", os.getenv("LA_SPARK_SHUFFLE_PARTITIONS", "24"))
        # Streaming-friendly defaults; overridden per-env by config/spark/spark-defaults.conf.
        .config("spark.sql.streaming.stateStore.stateSchemaCheck", "true")
    )
    if master := os.getenv("LA_SPARK_MASTER"):
        builder = builder.master(master)
    for key, value in (extra_conf or {}).items():
        builder = builder.config(key, value)
    return builder.getOrCreate()


def checkpoint_dir(job_name: str) -> str:
    """Each job owns its checkpoint dir — never share between jobs or job versions."""
    base = os.getenv("LA_SPARK_CHECKPOINT_DIR", "./.checkpoints")
    return f"{base.rstrip('/')}/{job_name}"


def kafka_options() -> dict[str, str]:
    """Connector options for readStream/writeStream — one place wires MSK security.

    PLAINTEXT locally; SASL_SSL (+SCRAM) against MSK, driven by the same LA_KAFKA_*
    environment variables the Python services use.
    """
    options = {
        "kafka.bootstrap.servers": os.getenv("LA_KAFKA_BOOTSTRAP_SERVERS", "localhost:29092"),
    }
    protocol = os.getenv("LA_KAFKA_SECURITY_PROTOCOL", "PLAINTEXT")
    if protocol != "PLAINTEXT":
        options["kafka.security.protocol"] = protocol
        mechanism = os.getenv("LA_KAFKA_SASL_MECHANISM", "")
        if mechanism:
            module = _JAAS_MODULES.get(mechanism.upper())
            if module is None:
                raise ValueError(f"unsupported LA_KAFKA_SASL_MECHANISM {mechanism!r}")
            username = os.getenv("LA_KAFKA_SASL_USERNAME", "")
            password = os.getenv("LA_KAFKA_SASL_PASSWORD", "")
            options["kafka.sasl.mechanism"] = mechanism.upper()
            options["kafka.sasl.jaas.config"] = (
                f'{module} required username="{username}" password="{password}";'
            )
    return options
