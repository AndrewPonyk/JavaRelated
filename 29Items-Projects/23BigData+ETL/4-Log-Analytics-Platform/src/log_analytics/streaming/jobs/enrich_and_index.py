"""Job 1 — Enrich & Index.

logs.raw ──parse──► enrich ──┬──► OpenSearch `la-logs` (idempotent bulk)
                             ├──► Kafka `logs.enriched` (canonical stream)
      └── unparseable ───────┴──► Kafka `logs.dlq`

Submit: ./scripts/submit_spark_job.sh enrich_and_index
"""

from __future__ import annotations

import os

from pyspark.sql import DataFrame
from pyspark.sql import functions as F

from log_analytics.common.kafka import TOPIC_LOGS_DLQ, TOPIC_LOGS_ENRICHED, TOPIC_LOGS_RAW
from log_analytics.streaming.session import build_spark_session, checkpoint_dir, kafka_options
from log_analytics.streaming.sinks.opensearch_sink import foreach_batch_indexer
from log_analytics.streaming.transforms.enrichment import enrich
from log_analytics.streaming.transforms.schema import parse_kafka_records

JOB_NAME = "enrich_and_index"
LOGS_WRITE_ALIAS = "la-logs"


def read_raw_stream(spark) -> DataFrame:
    return (
        spark.readStream.format("kafka")
        .options(**kafka_options())
        .option("subscribe", TOPIC_LOGS_RAW)
        .option("startingOffsets", os.getenv("LA_STARTING_OFFSETS", "latest"))
        # Bound catch-up batches after downtime — TECH-NOTES pitfall #10.
        .option("maxOffsetsPerTrigger", os.getenv("LA_MAX_OFFSETS_PER_TRIGGER", "100000"))
        .option("failOnDataLoss", "false")
        .load()
    )


def to_kafka_json(df: DataFrame, key_column: str | None = "service") -> DataFrame:
    """Rows → (key, value-as-JSON) shape expected by the Kafka sink."""
    value = F.to_json(F.struct([F.col(c) for c in df.columns])).alias("value")
    if key_column and key_column in df.columns:
        return df.select(F.col(key_column).alias("key"), value)
    return df.select(value)


def main() -> None:
    spark = build_spark_session(f"la-{JOB_NAME}")
    valid, dlq = parse_kafka_records(read_raw_stream(spark))
    enriched = enrich(valid).drop("topic", "partition", "offset", "kafka_timestamp")

    # 1) Search index (foreachBatch → bulk REST, deterministic _id).
    (
        enriched.drop("is_error")
        .writeStream.foreachBatch(foreach_batch_indexer(LOGS_WRITE_ALIAS, id_column="doc_id"))
        .option("checkpointLocation", checkpoint_dir(f"{JOB_NAME}/index"))
        .queryName("index_to_opensearch")
        .trigger(processingTime="10 seconds")
        .start()
    )

    # 2) Canonical enriched stream for downstream jobs.
    (
        to_kafka_json(enriched)
        .writeStream.format("kafka")
        .options(**kafka_options())
        .option("topic", TOPIC_LOGS_ENRICHED)
        .option("checkpointLocation", checkpoint_dir(f"{JOB_NAME}/enriched"))
        .queryName("publish_enriched")
        .start()
    )

    # 3) Poison messages, preserved with provenance for replay tooling.
    (
        to_kafka_json(dlq, key_column=None)
        .writeStream.format("kafka")
        .options(**kafka_options())
        .option("topic", TOPIC_LOGS_DLQ)
        .option("checkpointLocation", checkpoint_dir(f"{JOB_NAME}/dlq"))
        .queryName("publish_dlq")
        .start()
    )

    spark.streams.awaitAnyTermination()


if __name__ == "__main__":
    main()
