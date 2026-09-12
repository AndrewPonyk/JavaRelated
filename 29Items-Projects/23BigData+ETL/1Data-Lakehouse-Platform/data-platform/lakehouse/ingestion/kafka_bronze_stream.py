"""Kafka → Bronze: Structured Streaming ingestion of order events.

Bronze stores the *raw* payload untouched plus Kafka lineage columns — no parsing,
no filtering. Parsing failures therefore cannot lose data; they surface later at
the Bronze→Silver gate. At-least-once delivery is fine: Silver dedupes.

Security and backpressure are configuration-driven (see LakehouseSettings):
plaintext against local Kafka, SASL_SSL/IAM against MSK, and
KAFKA_MAX_OFFSETS_PER_TRIGGER to bound micro-batch size.

Run (local):
    python -m lakehouse.ingestion.kafka_bronze_stream --trigger available-now
"""

from __future__ import annotations

import argparse

from pyspark.sql import functions as F

from lakehouse.common.config import LakehouseSettings
from lakehouse.common.logging import get_logger
from lakehouse.common.spark import build_spark_session

JOB_NAME = "kafka_bronze_stream"

# Provided by the EMR runtime in cluster mode; resolved via ivy locally.
KAFKA_CONNECTOR = "org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.1"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--trigger",
        choices=["available-now", "continuous"],
        default="available-now",
        help="available-now: drain and stop (cost-controlled); continuous: micro-batches",
    )
    args = parser.parse_args()

    settings = LakehouseSettings.from_env()
    log = get_logger(JOB_NAME, topic=settings.orders_topic)
    spark = build_spark_session(
        JOB_NAME,
        settings,
        extra_packages=[KAFKA_CONNECTOR] if settings.local_mode else None,
    )

    reader = (
        spark.readStream.format("kafka")
        .options(**settings.kafka_source_options())
        .option("subscribe", settings.orders_topic)
        .option("startingOffsets", "earliest")
        .option("failOnDataLoss", settings.kafka_fail_on_data_loss)
    )
    if settings.kafka_max_offsets_per_trigger:
        reader = reader.option("maxOffsetsPerTrigger", settings.kafka_max_offsets_per_trigger)
    raw = reader.load()

    bronze = raw.select(
        F.col("key").cast("string").alias("event_key"),
        F.col("value").cast("string").alias("payload"),
        F.col("topic"),
        F.col("partition").alias("kafka_partition"),
        F.col("offset").alias("kafka_offset"),
        F.col("timestamp").alias("kafka_ts"),
        F.current_timestamp().alias("ingested_at"),
        F.to_date(F.current_timestamp()).alias("ingest_date"),  # partition column
    )

    writer = (
        bronze.writeStream.format("delta")
        .option("checkpointLocation", settings.checkpoint_path(JOB_NAME))
        .outputMode("append")
        .partitionBy("ingest_date")
    )

    target = settings.bronze_path("sales", "orders")
    log.info("starting bronze ingestion", extra={"context": {"target": target}})

    if args.trigger == "available-now":
        query = writer.trigger(availableNow=True).start(target)
    else:
        query = writer.trigger(processingTime="30 seconds").start(target)

    query.awaitTermination()
    progress = query.lastProgress or {}
    log.info(
        "bronze ingestion finished",
        extra={"context": {"rows_in_last_batch": progress.get("numInputRows", 0)}},
    )


if __name__ == "__main__":
    main()
