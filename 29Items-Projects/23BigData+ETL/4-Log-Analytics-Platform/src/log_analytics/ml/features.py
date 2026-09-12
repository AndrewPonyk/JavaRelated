"""Feature engineering for log anomaly detection.

Input: raw log records (timestamp, service, level, message).
Output: one row per (service, time window) with behavioral features — the Isolation
Forest never sees raw text, it sees how a service's logging *behaves*.
"""

from __future__ import annotations

import pandas as pd

FEATURE_COLUMNS: list[str] = [
    "log_count",  # volume — outages and floods both move this
    "error_count",
    "error_ratio",  # health independent of volume
    "warn_ratio",
    "unique_messages",  # cardinality spike = new failure mode / log storm
    "avg_message_len",  # stack traces are long; heartbeats are short
]

_ERROR_LEVELS = frozenset({"ERROR", "FATAL"})

# TODO(Phase 3): template-distribution entropy (drain3 template_id), p95 latency from
# attributes, inter-arrival-time variance, per-host dispersion.


def build_features(records: pd.DataFrame, window: str = "1min") -> pd.DataFrame:
    """Aggregate raw log records into per-service, per-window feature rows.

    `records` needs columns: timestamp, service, level, message. Extra columns ignored.
    Returns columns: window_start, service, *FEATURE_COLUMNS (empty frame if no input).
    """
    out_columns = ["window_start", "service", *FEATURE_COLUMNS]
    if records.empty:
        return pd.DataFrame(columns=out_columns)

    df = records.copy()
    df["timestamp"] = pd.to_datetime(df["timestamp"], utc=True, format="mixed")
    df["level"] = df["level"].fillna("INFO").astype(str).str.upper()
    df["message"] = df["message"].fillna("").astype(str)
    df["_is_error"] = df["level"].isin(_ERROR_LEVELS)
    df["_is_warn"] = df["level"].eq("WARN")
    df["_msg_len"] = df["message"].str.len()

    grouped = df.groupby([pd.Grouper(key="timestamp", freq=window), "service"], observed=True)
    features = grouped.agg(
        log_count=("message", "size"),
        error_count=("_is_error", "sum"),
        warn_count=("_is_warn", "sum"),
        unique_messages=("message", "nunique"),
        avg_message_len=("_msg_len", "mean"),
    )

    features["error_ratio"] = features["error_count"] / features["log_count"]
    features["warn_ratio"] = features["warn_count"] / features["log_count"]
    features = features.drop(columns=["warn_count"]).reset_index()
    features = features.rename(columns={"timestamp": "window_start"})

    # Isolation Forest wants floats and no NaNs.
    for column in FEATURE_COLUMNS:
        features[column] = (
            pd.to_numeric(features[column], errors="coerce").fillna(0.0).astype(float)
        )

    return features[out_columns]
