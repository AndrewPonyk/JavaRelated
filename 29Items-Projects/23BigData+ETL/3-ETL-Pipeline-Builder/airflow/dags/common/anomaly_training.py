"""Anomaly-model training for the weekly retrain loop.

The "model" for the streaming EWMA detector is a set of tuned per-metric
parameters (z_threshold, warmup, alpha). This module:

    fit_detector_params()  — robust-statistics fit over historical series
    evaluate_params()      — replay on a holdout window; gate on alert rate
    publish_params()       — Redis hash the processor hot-reloads (+ S3 archive)

_ewma_replay() replicates streaming/processor/anomaly/detector.py scoring
EXACTLY (including no-update-on-anomaly); the parity is pinned by
airflow/tests/test_common_logic.py. Pure Python — no airflow imports here.
"""

from __future__ import annotations

import json
import logging
import math
import statistics
import time

log = logging.getLogger(__name__)

MODEL_KEY = "anomaly:model"


# ── fitting ──────────────────────────────────────────────────────────────────


def fit_detector_params(
    series: dict[str, list[float]],
    *,
    base_alpha: float = 0.05,
    min_z: float = 4.0,
    max_z: float = 8.0,
    min_samples: int = 20,
) -> dict[str, dict]:
    """Per-metric thresholds from robust statistics (median/MAD, p99 spread).

    Metrics with too little history keep the processor defaults (omitted).
    """
    params: dict[str, dict] = {}
    for metric, values in series.items():
        clean = [float(v) for v in values if v is not None]
        if len(clean) < min_samples:
            log.info("%s: only %d samples — keeping default params", metric, len(clean))
            continue

        med = statistics.median(clean)
        mad = statistics.median(abs(v - med) for v in clean)
        robust_std = 1.4826 * mad

        if robust_std < 1e-9:
            z_threshold = min_z  # flat series: any deviation is interesting
        else:
            p99 = statistics.quantiles(clean, n=100)[98]
            spread = (p99 - med) / robust_std
            z_threshold = min(max(min_z, 1.25 * spread), max_z)

        params[metric] = {
            "alpha": base_alpha,
            "z_threshold": round(z_threshold, 2),
            "warmup": min(240, max(60, len(clean) // 10)),
        }
    return params


# ── evaluation ───────────────────────────────────────────────────────────────


def _ewma_replay(
    values: list[float], *, alpha: float, z_threshold: float, warmup: int, min_std: float = 1e-6
) -> list[bool]:
    """Exact replica of EwmaAnomalyDetector.score() over a series."""
    flags: list[bool] = []
    mean = var = 0.0
    n = 0
    for value in values:
        if n == 0:
            mean, var, n = value, 0.0, 1
            flags.append(False)
            continue
        std = max(math.sqrt(var), min_std)
        warmed = n >= warmup
        z = abs(value - mean) / std if warmed else 0.0
        is_anomaly = warmed and z >= z_threshold
        if not is_anomaly:
            diff = value - mean
            incr = alpha * diff
            mean += incr
            var = (1.0 - alpha) * (var + diff * incr)
        n += 1
        flags.append(is_anomaly)
    return flags


def evaluate_params(
    series: dict[str, list[float]],
    params: dict[str, dict],
    *,
    holdout_fraction: float = 0.3,
    max_alert_rate: float = 0.01,
) -> dict:
    """Replay each metric; the candidate is acceptable when the holdout alert
    rate stays under max_alert_rate for every tuned metric (noise budget)."""
    per_metric: dict[str, dict] = {}
    acceptable = True
    for metric, tuned in params.items():
        values = [float(v) for v in series.get(metric, []) if v is not None]
        if not values:
            continue
        flags = _ewma_replay(
            values,
            alpha=tuned["alpha"],
            z_threshold=tuned["z_threshold"],
            warmup=tuned["warmup"],
        )
        holdout_size = max(1, int(len(flags) * holdout_fraction))
        holdout = flags[-holdout_size:]
        rate = sum(holdout) / len(holdout)
        ok = rate <= max_alert_rate
        acceptable = acceptable and ok
        per_metric[metric] = {"alert_rate": round(rate, 4), "acceptable": ok}
    return {"per_metric": per_metric, "acceptable": acceptable}


# ── publish ──────────────────────────────────────────────────────────────────


def publish_params(
    params: dict[str, dict],
    version: str,
    *,
    redis_url: str,
    redis_client=None,
    s3_bucket: str = "",
    s3_prefix: str = "ml/models/anomaly",
    s3_client=None,
) -> dict:
    """Publish to the Redis hash the processor polls; archive to S3 when
    configured. Returns a summary for the task log / XCom."""
    payload = json.dumps(params)

    if redis_client is None:
        import redis  # lazy: keep DAG parse cheap

        redis_client = redis.Redis.from_url(redis_url, decode_responses=True)
    redis_client.hset(
        MODEL_KEY,
        mapping={"version": version, "params": payload, "published_at_ms": int(time.time() * 1000)},
    )

    archived_to = ""
    if s3_bucket:
        if s3_client is None:
            import boto3  # lazy

            s3_client = boto3.client("s3")
        key = f"{s3_prefix}/{version}/params.json"
        s3_client.put_object(Bucket=s3_bucket, Key=key, Body=payload.encode())
        archived_to = f"s3://{s3_bucket}/{key}"

    summary = {"version": version, "metrics": sorted(params), "archived_to": archived_to}
    log.info("published anomaly model %s", summary)
    return summary
