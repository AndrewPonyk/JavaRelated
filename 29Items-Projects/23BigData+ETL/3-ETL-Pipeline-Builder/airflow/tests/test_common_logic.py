"""Unit tests for the Airflow-adjacent pure logic (no Airflow install needed):
anomaly training/evaluation, health checks, cache warming.

Import path: pyproject pythonpath includes airflow/dags → `common` package.
"""

import json
from datetime import UTC, date, datetime

import pytest

from common.anomaly_training import (
    _ewma_replay,
    evaluate_params,
    fit_detector_params,
    publish_params,
)
from common.cache_warmer import DAY_MS, warm_daily_cache
from common.health import (
    ConsumerLagExceeded,
    HotStoreStale,
    check_consumer_lag,
    check_hot_store_freshness,
)


def stable_series(n=300, base=100.0):
    return [base + (i % 5) for i in range(n)]


# ── training ─────────────────────────────────────────────────────────────────


def test_fit_produces_bounded_thresholds():
    params = fit_detector_params(
        {
            "steady": stable_series(),
            "volatile": [100.0 * (1 + 0.5 * ((i * 7919) % 13 - 6) / 6) for i in range(300)],
            "tiny": [1.0, 2.0],  # below min_samples — omitted
        }
    )

    assert set(params) == {"steady", "volatile"}
    for tuned in params.values():
        assert 4.0 <= tuned["z_threshold"] <= 8.0
        assert 60 <= tuned["warmup"] <= 240
        assert 0.0 < tuned["alpha"] < 1.0


def test_fit_flat_series_uses_min_threshold():
    params = fit_detector_params({"flat": [42.0] * 100})
    assert params["flat"]["z_threshold"] == 4.0


def test_replay_matches_streaming_detector_exactly():
    """Training-side replica must stay numerically in sync with the processor."""
    from processor.anomaly.detector import EwmaAnomalyDetector

    values = stable_series(150) + [500.0] + stable_series(30, base=101.0)
    detector = EwmaAnomalyDetector(alpha=0.05, z_threshold=4.5, warmup=50)
    detector_flags = [detector.score("m", v).is_anomaly for v in values]

    replay_flags = _ewma_replay(values, alpha=0.05, z_threshold=4.5, warmup=50)

    assert replay_flags == detector_flags
    assert any(replay_flags)  # the spike was caught by both


def test_evaluate_accepts_quiet_candidate_and_rejects_noisy_one():
    series = {"m": stable_series(300)}
    quiet = {"m": {"alpha": 0.05, "z_threshold": 4.0, "warmup": 60}}
    assert evaluate_params(series, quiet)["acceptable"] is True

    spiky = {"m": stable_series(200) + [100.0 + (500.0 if i % 5 == 0 else 0.0) for i in range(100)]}
    report = evaluate_params(spiky, quiet)
    assert report["acceptable"] is False
    assert report["per_metric"]["m"]["alert_rate"] > 0.01


def test_publish_writes_redis_hash_and_optional_s3():
    class FakeSyncRedis:
        def __init__(self):
            self.hashes = {}

        def hset(self, name, mapping):
            self.hashes.setdefault(name, {}).update(mapping)

    class FakeS3:
        def __init__(self):
            self.objects = {}

        def put_object(self, Bucket, Key, Body):
            self.objects[(Bucket, Key)] = Body

    redis_client, s3 = FakeSyncRedis(), FakeS3()
    params = {"m": {"alpha": 0.05, "z_threshold": 5.0, "warmup": 60}}

    summary = publish_params(
        params,
        "20260704-abc",
        redis_url="",
        redis_client=redis_client,
        s3_bucket="artifacts",
        s3_client=s3,
    )

    stored = redis_client.hashes["anomaly:model"]
    assert stored["version"] == "20260704-abc"
    assert json.loads(stored["params"]) == params
    assert ("artifacts", "ml/models/anomaly/20260704-abc/params.json") in s3.objects
    assert summary["metrics"] == ["m"]


# ── health checks ────────────────────────────────────────────────────────────


class FakeFreshnessRedis:
    def __init__(self, data):
        self._data = data

    def hgetall(self, key):
        return self._data


def test_freshness_ok_returns_staleness():
    now = 1_000_000.0
    client = FakeFreshnessRedis(
        {"window_start_ms": str(int(now * 1000) - 5_500), "window_ms": "500"}
    )
    staleness = check_hot_store_freshness(
        "unused", client=client, now=now, max_staleness_seconds=30
    )
    assert staleness == pytest.approx(5.0)


def test_freshness_raises_when_stale_or_missing():
    now = 1_000_000.0
    stale = FakeFreshnessRedis(
        {"window_start_ms": str(int(now * 1000) - 120_000), "window_ms": "500"}
    )
    with pytest.raises(HotStoreStale):
        check_hot_store_freshness("unused", client=stale, now=now, max_staleness_seconds=30)
    with pytest.raises(HotStoreStale):
        check_hot_store_freshness("unused", client=FakeFreshnessRedis({}), now=now)


class FakeCloudWatch:
    def __init__(self, values):
        self._values = values
        self.queries = None

    def get_metric_data(self, **kwargs):
        self.queries = kwargs
        return {"MetricDataResults": [{"Values": self._values}]}


def test_consumer_lag_under_threshold_passes():
    cw = FakeCloudWatch([1_234.0])
    lag = check_consumer_lag(
        cluster_name="etl-dev",
        consumer_group="metrics-processor",
        region="eu-central-1",
        threshold=10_000,
        client=cw,
    )
    assert lag == 1_234.0
    dims = cw.queries["MetricDataQueries"][0]["MetricStat"]["Metric"]["Dimensions"]
    assert {"Name": "Consumer Group", "Value": "metrics-processor"} in dims


def test_consumer_lag_over_threshold_raises():
    with pytest.raises(ConsumerLagExceeded):
        check_consumer_lag(
            cluster_name="etl-dev",
            consumer_group="metrics-processor",
            region="eu-central-1",
            threshold=10_000,
            client=FakeCloudWatch([50_000.0]),
        )


# ── cache warmer ─────────────────────────────────────────────────────────────


class FakeWarmRedis:
    def __init__(self):
        self.zsets: dict[str, dict[str, float]] = {}
        self.expirations: dict[str, int] = {}

    def zadd(self, key, mapping):
        self.zsets.setdefault(key, {}).update(mapping)

    def zremrangebyscore(self, key, low, high):
        zset = self.zsets.get(key, {})
        lo = float("-inf") if low == "-inf" else float(low)
        hi = float("inf") if high == "+inf" else float(high)
        for member in [m for m, s in zset.items() if lo <= s <= hi]:
            del zset[member]

    def expire(self, key, seconds):
        self.expirations[key] = seconds


def test_warm_daily_cache_writes_day_buckets():
    client = FakeWarmRedis()
    rows = [
        ("orders_count", date(2026, 7, 2), 1200),
        ("orders_count", date(2026, 7, 3), 1350),
        ("revenue_total", date(2026, 7, 3), 88_000.5),
    ]

    written = warm_daily_cache(rows, client=client, retention_days=90)

    assert written == 3
    orders = client.zsets["metric:orders_count:daily"]
    assert len(orders) == 2
    member = next(m for m in orders if json.loads(m)["value"] == 1350.0)
    parsed = json.loads(member)
    assert parsed["window_ms"] == DAY_MS
    expected_ms = int(datetime(2026, 7, 3, tzinfo=UTC).timestamp() * 1000)
    assert parsed["window_start_ms"] == expected_ms
    assert client.expirations["metric:orders_count:daily"] == 90 * 86_400


def test_warm_daily_cache_is_idempotent_per_day():
    client = FakeWarmRedis()
    rows = [("orders_count", date(2026, 7, 3), 1350)]
    warm_daily_cache(rows, client=client)
    warm_daily_cache([("orders_count", date(2026, 7, 3), 1400)], client=client)  # restated

    orders = client.zsets["metric:orders_count:daily"]
    assert [json.loads(m)["value"] for m in orders] == [1400.0]
