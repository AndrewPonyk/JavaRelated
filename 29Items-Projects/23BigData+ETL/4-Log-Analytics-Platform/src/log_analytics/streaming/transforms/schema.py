"""Spark schema for LogEvent + Kafka payload parsing with DLQ split.

Keep in sync with log_analytics.common.models.LogEvent (contract tests TODO).
"""

from __future__ import annotations

from pyspark.sql import DataFrame
from pyspark.sql import functions as F
from pyspark.sql.types import MapType, StringType, StructField, StructType, TimestampType


def log_event_schema() -> StructType:
    return StructType(
        [
            StructField("timestamp", TimestampType(), nullable=False),
            StructField("service", StringType(), nullable=False),
            StructField("env", StringType(), nullable=True),
            StructField("level", StringType(), nullable=True),
            StructField("message", StringType(), nullable=True),
            StructField("host", StringType(), nullable=True),
            StructField("trace_id", StringType(), nullable=True),
            StructField("span_id", StringType(), nullable=True),
            # flat_object in OpenSearch; keep values as strings at the Spark boundary.
            StructField("attributes", MapType(StringType(), StringType()), nullable=True),
        ]
    )


def parse_kafka_records(kafka_df: DataFrame) -> tuple[DataFrame, DataFrame]:
    """Split a raw Kafka stream into (valid_events, dlq_records).

    valid_events: LogEvent columns + kafka provenance (topic/partition/offset).
    dlq_records:  original payload + error tag, ready for the `logs.dlq` sink.
    """
    raw = kafka_df.select(
        F.col("value").cast("string").alias("raw_value"),
        F.col("topic"),
        F.col("partition"),
        F.col("offset"),
        F.col("timestamp").alias("kafka_timestamp"),
    )

    parsed = raw.withColumn("event", F.from_json(F.col("raw_value"), log_event_schema()))

    valid = parsed.where(
        F.col("event").isNotNull()
        & F.col("event.timestamp").isNotNull()
        & F.col("event.service").isNotNull()
    ).select("event.*", "topic", "partition", "offset", "kafka_timestamp")

    dlq = parsed.where(
        F.col("event").isNull()
        | F.col("event.timestamp").isNull()
        | F.col("event.service").isNull()
    ).select(
        F.col("raw_value").alias("payload"),
        F.lit("schema_validation_failed").alias("error"),
        F.col("topic").alias("source_topic"),
        F.col("partition").alias("source_partition"),
        F.col("offset").alias("source_offset"),
    )
    return valid, dlq
