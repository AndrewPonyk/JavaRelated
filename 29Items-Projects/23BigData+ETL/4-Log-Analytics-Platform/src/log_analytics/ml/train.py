"""Offline training CLI for the log anomaly detector.

    python -m log_analytics.ml.train --source synthetic                 # bootstrap a first model
    python -m log_analytics.ml.train --source opensearch --from 2026-06-01 --to 2026-07-01
    python -m log_analytics.ml.train --source parquet --input data/logs.parquet

Scheduled weekly in AWS (EMR Serverless batch run or ECS scheduled task); the scoring job
hot-reloads whatever `latest` points at. Evaluation is a time-ordered holdout: the last 20%
of windows are never seen during fit, so the reported score distribution is honest.
"""

from __future__ import annotations

import argparse
import logging

import httpx
import numpy as np
import pandas as pd

from log_analytics.common.logging import configure_logging
from log_analytics.ml.features import build_features
from log_analytics.ml.isolation_forest import LogAnomalyDetector, ModelMetadata
from log_analytics.ml.registry import get_registry, new_version
from log_analytics.ml.synthetic import generate_log_records

logger = logging.getLogger(__name__)

LOGS_ALIAS = "la-logs"
_PAGE_SIZE = 1000
MIN_TRAINING_WINDOWS = 100
HOLDOUT_FRACTION = 0.2


def fetch_opensearch(
    url: str,
    from_ts: str | None,
    to_ts: str | None,
    limit: int = 500_000,
    auth: tuple[str, str] | None = None,
    transport: httpx.BaseTransport | None = None,
) -> pd.DataFrame:
    """Pull raw log records from la-logs via search_after pagination (stable sort)."""
    time_range: dict[str, str] = {}
    if from_ts:
        time_range["gte"] = from_ts
    if to_ts:
        time_range["lte"] = to_ts
    query = {"range": {"@timestamp": time_range}} if time_range else {"match_all": {}}

    rows: list[dict] = []
    search_after: list | None = None
    with httpx.Client(
        base_url=url.rstrip("/"), auth=auth, timeout=30.0, transport=transport
    ) as client:
        while len(rows) < limit:
            body: dict = {
                "size": min(_PAGE_SIZE, limit - len(rows)),
                "query": query,
                "sort": [{"@timestamp": "asc"}, {"_id": "asc"}],
                "_source": ["@timestamp", "service", "level", "message"],
            }
            if search_after is not None:
                body["search_after"] = search_after
            resp = client.post(f"/{LOGS_ALIAS}/_search", json=body)
            resp.raise_for_status()
            hits = resp.json()["hits"]["hits"]
            if not hits:
                break
            for hit in hits:
                source = hit["_source"]
                rows.append(
                    {
                        "timestamp": source["@timestamp"],
                        "service": source.get("service", "unknown"),
                        "level": source.get("level", "INFO"),
                        "message": source.get("message", ""),
                    }
                )
            search_after = hits[-1]["sort"]
    logger.info("fetched %s records from %s", len(rows), url)
    return pd.DataFrame(rows, columns=["timestamp", "service", "level", "message"])


def fetch_records(args: argparse.Namespace) -> pd.DataFrame:
    """Load raw log records (timestamp, service, level, message) for training."""
    if args.source == "parquet":
        if not args.input:
            raise SystemExit("--source parquet requires --input <path> (needs pyarrow installed)")
        return pd.read_parquet(args.input)
    if args.source == "opensearch":
        from log_analytics.common.config import get_settings

        settings = get_settings()
        return fetch_opensearch(
            args.url or settings.opensearch_url,
            from_ts=args.from_ts,
            to_ts=args.to_ts,
            limit=args.limit,
            auth=settings.opensearch_auth,
        )
    if args.source == "synthetic":
        return generate_log_records(minutes=args.minutes)
    raise ValueError(f"unknown source {args.source!r}")


def train(
    records: pd.DataFrame, window: str, contamination: str | float
) -> tuple[LogAnomalyDetector, ModelMetadata]:
    """Fit on the first 80% of windows (time-ordered), evaluate on the held-out tail."""
    features = build_features(records, window=window).sort_values("window_start")
    if len(features) < MIN_TRAINING_WINDOWS:
        raise SystemExit(
            f"refusing to train on {len(features)} windows (<{MIN_TRAINING_WINDOWS}) — "
            "collect more history or use --source synthetic"
        )

    split = int(len(features) * (1 - HOLDOUT_FRACTION))
    train_set, holdout = features.iloc[:split], features.iloc[split:]

    detector = LogAnomalyDetector(contamination=contamination).fit(train_set)
    holdout_scores = detector.score(holdout)

    metadata = ModelMetadata(
        version=new_version(),
        n_samples=len(train_set),
        params={"window": window, "contamination": str(contamination)},
        metrics={
            "holdout_windows": float(len(holdout)),
            "holdout_score_mean": float(np.mean(holdout_scores)),
            "holdout_score_p95": float(np.percentile(holdout_scores, 95)),
            "holdout_score_p99": float(np.percentile(holdout_scores, 99)),
            "holdout_rate_above_0_8": float(np.mean(holdout_scores >= 0.8)),
        },
    )
    return detector, metadata


def main(argv: list[str] | None = None) -> str:
    configure_logging(service="ml-train")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", choices=["parquet", "opensearch", "synthetic"], required=True)
    parser.add_argument("--input", help="parquet path (for --source parquet)")
    parser.add_argument("--url", help="OpenSearch URL override (for --source opensearch)")
    parser.add_argument("--from", dest="from_ts", help="history lower bound, ISO-8601")
    parser.add_argument("--to", dest="to_ts", help="history upper bound, ISO-8601")
    parser.add_argument("--limit", type=int, default=500_000, help="max records to fetch")
    parser.add_argument("--minutes", type=int, default=360, help="synthetic traffic duration")
    parser.add_argument(
        "--window", default="1min", help="aggregation window (must match the scoring job)"
    )
    parser.add_argument("--contamination", default="auto")
    parser.add_argument("--registry", default=None, help="override LA_MODEL_REGISTRY_URI")
    args = parser.parse_args(argv)

    records = fetch_records(args)
    logger.info("training on %s raw records", len(records))

    detector, metadata = train(records, window=args.window, contamination=args.contamination)

    from log_analytics.common.config import get_settings

    registry_uri = args.registry or get_settings().model_registry_uri
    version = get_registry(registry_uri).save(detector, metadata)
    print(f"model {version} saved to {registry_uri}")
    print(f"holdout metrics: {metadata.metrics}")
    return version


if __name__ == "__main__":
    main()
