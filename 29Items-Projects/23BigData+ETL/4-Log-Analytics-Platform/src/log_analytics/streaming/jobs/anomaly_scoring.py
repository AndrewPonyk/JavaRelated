"""Job 3 — ML Anomaly Scoring (Isolation Forest).

logs.enriched ─► 1-min features per service ─► IsolationForest.score ─┬─► logs.anomalies
                                                                      └─► la-anomalies index

The model is trained offline (ml/train.py), versioned in the registry (S3/local), and
loaded lazily once per executor. The registry's `latest` pointer is re-checked every
LA_MODEL_REFRESH_SECONDS (default 300) so a weekly retrain rolls out without restarting
the job. Feature engineering is shared with training via log_analytics.ml.features —
train/serve skew is a code-review problem, not a runtime one.

Windows with fewer than LA_ANOMALY_MIN_EVENTS events are skipped: five log lines have
no statistical shape, and paging on them is pure noise.
"""

from __future__ import annotations

import logging
import os
import time
from collections.abc import Iterator
from typing import Any

import pandas as pd
from pyspark.sql import DataFrame
from pyspark.sql import functions as F

from log_analytics.common.kafka import TOPIC_LOGS_ANOMALIES, TOPIC_LOGS_ENRICHED
from log_analytics.ml.features import FEATURE_COLUMNS, build_features
from log_analytics.streaming.session import build_spark_session, checkpoint_dir, kafka_options
from log_analytics.streaming.sinks.opensearch_sink import foreach_batch_indexer
from log_analytics.streaming.transforms.schema import log_event_schema

logger = logging.getLogger(__name__)

JOB_NAME = "anomaly_scoring"
ANOMALIES_WRITE_ALIAS = "la-anomalies"

_SCORED_SCHEMA = (
    "window_start timestamp, service string, "
    + ", ".join(f"{c} double" for c in FEATURE_COLUMNS)
    + ", score double, is_anomaly boolean, model_version string"
)

# Executor-local model cache: one load per worker process, refreshed on version change.
_CACHE: dict[str, Any] = {}


def _get_detector():
    """Load (and periodically hot-reload) the latest model from the registry."""
    from log_analytics.ml.registry import get_registry

    refresh_seconds = float(os.getenv("LA_MODEL_REFRESH_SECONDS", "300"))
    now = time.monotonic()

    if "registry" not in _CACHE:
        _CACHE["registry"] = get_registry(os.getenv("LA_MODEL_REGISTRY_URI", "./models"))
    registry = _CACHE["registry"]

    needs_check = (
        "detector" not in _CACHE or now - float(_CACHE.get("checked_at", 0.0)) > refresh_seconds
    )
    if needs_check:
        latest = registry.latest_version()
        if latest is None:
            raise RuntimeError(
                "model registry is empty — bootstrap one first: "
                "python -m log_analytics.ml.train --source synthetic"
            )
        if _CACHE.get("version") != latest:
            detector, metadata = registry.load(latest)
            _CACHE["detector"] = detector
            _CACHE["version"] = metadata.version
            logger.info("loaded anomaly model version %s", metadata.version)
        _CACHE["checked_at"] = now
    return _CACHE["detector"], str(_CACHE["version"])


def score_partitions(frames: Iterator[pd.DataFrame]) -> Iterator[pd.DataFrame]:
    """mapInPandas scorer: raw events in, per-(service, window) anomaly rows out."""
    threshold = float(os.getenv("LA_ANOMALY_ALERT_THRESHOLD", "0.8"))
    min_events = int(os.getenv("LA_ANOMALY_MIN_EVENTS", "10"))
    detector, version = _get_detector()
    for frame in frames:
        if frame.empty:
            continue
        features = build_features(frame, window="1min")
        features = features[features["log_count"] >= min_events]
        if features.empty:
            continue
        features = features.copy()
        features["score"] = detector.score(features)
        features["is_anomaly"] = features["score"] >= threshold
        features["model_version"] = version
        yield features


def read_enriched(spark) -> DataFrame:
    raw = (
        spark.readStream.format("kafka")
        .options(**kafka_options())
        .option("subscribe", TOPIC_LOGS_ENRICHED)
        .option("startingOffsets", os.getenv("LA_STARTING_OFFSETS", "latest"))
        .option("maxOffsetsPerTrigger", os.getenv("LA_MAX_OFFSETS_PER_TRIGGER", "100000"))
        .option("failOnDataLoss", "false")
        .load()
    )
    return raw.select(
        F.from_json(F.col("value").cast("string"), log_event_schema()).alias("e")
    ).select("e.timestamp", "e.service", "e.level", "e.message")


def main() -> None:
    spark = build_spark_session(f"la-{JOB_NAME}")

    scored = (
        read_enriched(spark)
        .withWatermark("timestamp", "2 minutes")
        # Group by service so each pandas frame holds one service's events; windowing
        # happens inside build_features (per-micro-batch approximation of window close).
        .repartition("service")
        .mapInPandas(score_partitions, schema=_SCORED_SCHEMA)  # type: ignore[arg-type]
        .withColumn("window_end", F.col("window_start") + F.expr("INTERVAL 1 MINUTE"))
    )

    # Kafka: anomalies stream for the alerting engine.
    (
        scored.select(F.to_json(F.struct([F.col(c) for c in scored.columns])).alias("value"))
        .writeStream.format("kafka")
        .options(**kafka_options())
        .option("topic", TOPIC_LOGS_ANOMALIES)
        .option("checkpointLocation", checkpoint_dir(f"{JOB_NAME}/kafka"))
        .queryName("publish_anomalies")
        .start()
    )

    # OpenSearch: history for the anomaly-triage dashboard.
    (
        scored.writeStream.foreachBatch(
            foreach_batch_indexer(ANOMALIES_WRITE_ALIAS, id_column=None)
        )
        .option("checkpointLocation", checkpoint_dir(f"{JOB_NAME}/index"))
        .queryName("index_anomalies")
        .trigger(processingTime="30 seconds")
        .start()
    )

    spark.streams.awaitAnyTermination()


if __name__ == "__main__":
    main()
