#!/usr/bin/env python3
"""Offline trainer: seasonal baselines for the streaming anomaly detector.

Reads historical 1m aggregates from PostgreSQL, fits an hour-of-week baseline
(median/MAD per 168 weekly buckets — robust to the very anomalies we hunt),
optionally versions the artifact to S3, and publishes it to the compacted control
topic `ml.model-updates.v1` — from where flink-anomaly-job picks it up via
broadcast state WITHOUT a redeploy. Contract: ml/README.md + ModelParams.java.

Usage:
    python training/train_anomaly_model.py --metrics orders.completed --dry-run
    python training/train_anomaly_model.py --metrics orders.completed payments.captured \
        --days 28 --publish --bootstrap localhost:29092
    python training/train_anomaly_model.py --metrics orders.completed \
        --publish --s3-bucket rtap-dev-artifacts
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import uuid

import pandas as pd

HOURS_PER_WEEK = 168
# MAD → std of a normal distribution; keeps thresholds comparable to z-scores.
MAD_TO_STD = 1.4826
# Below this many samples a weekly bucket is statistically meaningless — fall back
# to the global baseline instead of alerting on noise.
MIN_SAMPLES_PER_BUCKET = 3


def postgres_dsn(args: argparse.Namespace) -> str:
    if args.dsn:
        return args.dsn
    return (
        f"host={os.environ.get('POSTGRES_HOST', 'localhost')} "
        f"port={os.environ.get('POSTGRES_PORT', '5432')} "
        f"dbname={os.environ.get('POSTGRES_DB', 'analytics')} "
        f"user={os.environ.get('POSTGRES_USER', 'analytics')} "
        f"password={os.environ.get('POSTGRES_PASSWORD', 'analytics_local_pw')}"
    )


def load_history(metric_key: str, days: int, dsn: str) -> pd.DataFrame:
    """Pull (window_start, value_sum) for the metric's 1m rollups, dimensions merged."""
    import psycopg  # imported lazily so pure-math tests need no driver

    query = """
        SELECT window_start, SUM(value_sum) AS value_sum
        FROM metric_aggregates
        WHERE metric_key = %s
          AND window_size = '1m'
          AND window_start >= now() - make_interval(days => %s)
        GROUP BY window_start
        ORDER BY window_start
    """
    with psycopg.connect(dsn) as connection:
        rows = connection.execute(query, (metric_key, days)).fetchall()
    frame = pd.DataFrame(rows, columns=["window_start", "value_sum"])
    if not frame.empty:
        frame["window_start"] = pd.to_datetime(frame["window_start"], utc=True)
        frame["value_sum"] = frame["value_sum"].astype(float)
    return frame


def hour_of_week(timestamps: pd.Series) -> pd.Series:
    """Monday 00:00 UTC = bucket 0 … Sunday 23:00 = 167 (matches ModelParams.java)."""
    utc = timestamps.dt.tz_convert("UTC")
    return utc.dt.dayofweek * 24 + utc.dt.hour


def fit_seasonal_baseline(frame: pd.DataFrame) -> tuple[list[float], list[float]]:
    """Robust per-bucket location/scale: median and MAD·1.4826.

    Buckets with too few observations fall back to the global statistics, so a
    fresh deployment degrades gracefully instead of hallucinating seasonality.
    """
    if frame.empty:
        raise ValueError("no history — refusing to train an empty model")

    values = frame["value_sum"].astype(float)
    global_median = float(values.median())
    global_mad = float((values - global_median).abs().median()) * MAD_TO_STD

    frame = frame.assign(bucket=hour_of_week(frame["window_start"]))
    means = [global_median] * HOURS_PER_WEEK
    stds = [global_mad] * HOURS_PER_WEEK

    for bucket, group in frame.groupby("bucket"):
        if len(group) < MIN_SAMPLES_PER_BUCKET:
            continue
        bucket_values = group["value_sum"].astype(float)
        median = float(bucket_values.median())
        mad = float((bucket_values - median).abs().median()) * MAD_TO_STD
        means[int(bucket)] = median
        stds[int(bucket)] = mad if mad > 0 else global_mad

    return means, stds


def build_params(metric_key: str, means: list[float], stds: list[float],
                 z_threshold: float) -> dict:
    """The `ml.model-updates.v1` message — MUST match ModelParams.java field names."""
    version = "{}-{}".format(
        dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%MZ"),
        uuid.uuid4().hex[:6],
    )
    return {
        "metricKey": metric_key,
        "modelVersion": version,
        "zThreshold": z_threshold,
        "seasonalMeans": [round(m, 4) for m in means],
        "seasonalStds": [round(s, 4) for s in stds],
    }


def upload_to_s3(params: dict, bucket: str) -> str:
    """Versioned artifact + rolling latest.json → rollback = re-publish an old version."""
    import boto3

    body = json.dumps(params).encode()
    key = f"ml/{params['metricKey']}/{params['modelVersion']}.json"
    s3 = boto3.client("s3")
    s3.put_object(Bucket=bucket, Key=key, Body=body, ContentType="application/json")
    s3.put_object(Bucket=bucket, Key=f"ml/{params['metricKey']}/latest.json",
                  Body=body, ContentType="application/json")
    return key


def publish_to_kafka(params: dict, bootstrap: str) -> None:
    """Latest-wins per metricKey on the compacted control topic."""
    from kafka import KafkaProducer

    producer = KafkaProducer(bootstrap_servers=bootstrap, acks="all")
    producer.send(
        "ml.model-updates.v1",
        key=params["metricKey"].encode(),
        value=json.dumps(params).encode(),
    )
    producer.flush()
    producer.close()


def train_metric(metric_key: str, args: argparse.Namespace) -> dict | None:
    frame = load_history(metric_key, args.days, postgres_dsn(args))
    if frame.empty:
        print(f"WARN {metric_key}: no 1m history - skipped")  # ASCII-only: Windows consoles are cp1252
        return None
    means, stds = fit_seasonal_baseline(frame)
    params = build_params(metric_key, means, stds, args.z_threshold)
    print(f"OK {metric_key}: fitted {len(frame)} windows -> model {params['modelVersion']}")
    return params


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--metrics", nargs="+", required=True, help="metric keys to train")
    parser.add_argument("--days", type=int, default=28, help="history window")
    parser.add_argument("--z-threshold", type=float, default=4.0)
    parser.add_argument("--dsn", default=None, help="postgres DSN (defaults from POSTGRES_* env)")
    parser.add_argument("--s3-bucket", default=None, help="artifact bucket (skip to not upload)")
    parser.add_argument("--bootstrap", default=os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "localhost:29092"))
    parser.add_argument("--publish", action="store_true", help="publish to the control topic")
    parser.add_argument("--dry-run", action="store_true", help="print params, write nothing")
    args = parser.parse_args()

    for metric_key in args.metrics:
        params = train_metric(metric_key, args)
        if params is None:
            continue
        if args.dry_run:
            print(json.dumps(params, indent=2)[:2000])
            continue
        if args.s3_bucket:
            key = upload_to_s3(params, args.s3_bucket)
            print(f"   -> s3://{args.s3_bucket}/{key}")
        if args.publish:
            publish_to_kafka(params, args.bootstrap)
            print(f"   -> published to ml.model-updates.v1 via {args.bootstrap}")


if __name__ == "__main__":
    main()
