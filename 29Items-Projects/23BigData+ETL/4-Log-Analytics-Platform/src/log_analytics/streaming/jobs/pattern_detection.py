"""Job 2 — Pattern Detection.

logs.enriched ─► tumbling windowed metrics per service ─► threshold rules ─► alerts.events

Rules come from config/alert_rules.yaml (LA_RULES_PATH). Rules are grouped by their
`window` (30s / 1m / 5m …) and each distinct window gets its own aggregation + streaming
query (Structured Streaming does not allow chaining several aggregations in one query).
The alerting engine resolves channels/severity routing from the same rules file;
`anomaly_score` rules belong to the engine and are skipped here.
"""

from __future__ import annotations

import logging
import os
from collections import defaultdict

from pyspark.sql import DataFrame
from pyspark.sql import functions as F

from log_analytics.alerting.rules import ThresholdRule, load_rules, parse_window
from log_analytics.common.kafka import TOPIC_ALERTS, TOPIC_LOGS_ENRICHED
from log_analytics.streaming.session import build_spark_session, checkpoint_dir, kafka_options
from log_analytics.streaming.transforms.schema import log_event_schema

logger = logging.getLogger(__name__)

JOB_NAME = "pattern_detection"

# Metrics this job materializes per (service, window).
COMPUTABLE = {"log_count", "error_count", "error_ratio", "warn_ratio"}

_SQL_OPS = {"gt": ">", "gte": ">=", "lt": "<", "lte": "<="}


def read_enriched_stream(spark) -> DataFrame:
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
        F.from_json(F.col("value").cast("string"), log_event_schema()).alias("event")
    ).select("event.*")


def spark_window_duration(rule_window: str) -> str:
    """'5m' → '300 seconds' (Spark's window() duration syntax)."""
    return f"{int(parse_window(rule_window).total_seconds())} seconds"


def windowed_metrics(events: DataFrame, duration: str) -> DataFrame:
    """Tumbling windowed metrics per service. Late data beyond the watermark is excluded
    from aggregates (still searchable in la-logs) — TECH-NOTES pitfall #8."""
    return (
        events.withWatermark("timestamp", "2 minutes")
        .groupBy(F.window("timestamp", duration).alias("w"), F.col("service"))
        .agg(
            F.count("*").cast("double").alias("log_count"),
            F.sum(F.when(F.col("level").isin("ERROR", "FATAL"), 1).otherwise(0))
            .cast("double")
            .alias("error_count"),
            F.sum(F.when(F.col("level") == "WARN", 1).otherwise(0))
            .cast("double")
            .alias("warn_count"),
        )
        .withColumn("error_ratio", F.col("error_count") / F.col("log_count"))
        .withColumn("warn_ratio", F.col("warn_count") / F.col("log_count"))
    )


def apply_rules(metrics: DataFrame, rules: list[ThresholdRule]) -> DataFrame:
    """Union of per-rule matches, shaped as AlertEvent-compatible rows.

    `context` is emitted as a JSON string (Kafka value stays flat); the alerting engine
    parses it back and resolves channels from the rule id.
    """
    alerts: DataFrame | None = None
    for rule in rules:
        matched = metrics.where(
            F.expr(f"{rule.metric} {_SQL_OPS[rule.op]} {rule.threshold}")
        ).select(
            F.lit(rule.id).alias("rule_id"),
            F.lit(rule.severity.value).alias("severity"),
            F.lit("pattern").alias("source"),
            F.col("service"),
            F.concat(F.lit(f"[{rule.name}] "), F.col("service")).alias("title"),
            F.lit(rule.description).alias("description"),
            F.concat_ws(
                "|", F.lit(rule.id), F.col("service"), F.col("w.start").cast("string")
            ).alias("dedup_key"),
            F.to_json(
                F.struct(
                    F.col("w.start").alias("window_start"),
                    F.col(rule.metric).alias("observed"),
                    F.lit(rule.threshold).alias("threshold"),
                )
            ).alias("context"),
        )
        alerts = matched if alerts is None else alerts.unionByName(matched)
    assert alerts is not None  # caller guarantees non-empty rule group
    return alerts


def runnable_rules(rules: list[ThresholdRule]) -> dict[str, list[ThresholdRule]]:
    """Enabled rules this job can evaluate, grouped by window duration."""
    groups: dict[str, list[ThresholdRule]] = defaultdict(list)
    for rule in rules:
        if rule.enabled and rule.metric in COMPUTABLE:
            groups[spark_window_duration(rule.window)].append(rule)
    return dict(groups)


def main() -> None:
    spark = build_spark_session(f"la-{JOB_NAME}")
    rules = load_rules(os.getenv("LA_RULES_PATH", "config/alert_rules.yaml"))
    groups = runnable_rules(rules)
    if not groups:
        raise SystemExit("no runnable pattern rules configured — nothing to do")

    events = read_enriched_stream(spark)
    for duration, group in sorted(groups.items()):
        safe = duration.replace(" ", "_")
        alerts = apply_rules(windowed_metrics(events, duration), group)
        (
            alerts.select(F.to_json(F.struct([F.col(c) for c in alerts.columns])).alias("value"))
            .writeStream.format("kafka")
            .options(**kafka_options())
            .option("topic", TOPIC_ALERTS)
            .option("checkpointLocation", checkpoint_dir(f"{JOB_NAME}/{safe}"))
            .outputMode("update")
            .queryName(f"alerts_{safe}")
            .start()
        )
        logger.info("started %s query with %d rule(s)", duration, len(group))

    # "Service went silent" detection lives in the alerting engine (absence of data is
    # invisible to windowed aggregations) — see AlertingEngine.check_silent_services.
    spark.streams.awaitAnyTermination()


if __name__ == "__main__":
    main()
