"""Speed-layer health checks used by the streaming_health watchdog DAG.

Pure logic with injectable clients (unit-testable without airflow/aws/redis);
lazy imports keep DAG parse time flat.
"""

from __future__ import annotations

import logging
import time
from datetime import UTC, datetime, timedelta

log = logging.getLogger(__name__)


class HotStoreStale(RuntimeError):
    """Hot store has no data or the newest window is older than the SLO."""


class ConsumerLagExceeded(RuntimeError):
    """MSK consumer group lag is above the alerting threshold."""


def check_hot_store_freshness(
    redis_url: str,
    *,
    max_staleness_seconds: float = 30.0,
    metric: str = "orders_per_second",
    client=None,
    now: float | None = None,
) -> float:
    """Return staleness in seconds; raise HotStoreStale beyond the SLO.

    A frozen dashboard must page — silence is the failure mode we fear most
    (ARCHITECTURE §2.6).
    """
    if client is None:
        import redis  # lazy

        client = redis.Redis.from_url(redis_url, decode_responses=True)

    data = client.hgetall(f"metric:{metric}")
    if not data:
        raise HotStoreStale(f"no hot-store data for {metric} — processor down or never started?")

    window_end_s = (int(data["window_start_ms"]) + int(data["window_ms"])) / 1000.0
    staleness = (now if now is not None else time.time()) - window_end_s
    if staleness > max_staleness_seconds:
        raise HotStoreStale(
            f"{metric} hot store is {staleness:.1f}s stale (SLO {max_staleness_seconds}s)"
        )
    log.info("hot store fresh: %s is %.1fs old", metric, staleness)
    return staleness


def check_consumer_lag(
    *,
    cluster_name: str,
    consumer_group: str,
    region: str,
    threshold: int = 10_000,
    client=None,
) -> float:
    """Latest MSK MaxOffsetLag for the group; raise ConsumerLagExceeded above
    threshold. Returns the observed lag (0.0 when no datapoints yet)."""
    if client is None:
        import boto3  # lazy

        client = boto3.client("cloudwatch", region_name=region)

    now = datetime.now(UTC)
    response = client.get_metric_data(
        MetricDataQueries=[
            {
                "Id": "lag",
                "MetricStat": {
                    "Metric": {
                        "Namespace": "AWS/Kafka",
                        "MetricName": "MaxOffsetLag",
                        "Dimensions": [
                            {"Name": "Cluster Name", "Value": cluster_name},
                            {"Name": "Consumer Group", "Value": consumer_group},
                        ],
                    },
                    "Period": 60,
                    "Stat": "Maximum",
                },
            }
        ],
        StartTime=now - timedelta(minutes=10),
        EndTime=now,
    )
    values = response["MetricDataResults"][0].get("Values", [])
    lag = float(values[0]) if values else 0.0
    if lag > threshold:
        raise ConsumerLagExceeded(
            f"consumer group '{consumer_group}' lag {lag:.0f} > threshold {threshold}"
        )
    log.info("consumer lag ok: %.0f (threshold %d)", lag, threshold)
    return lag
