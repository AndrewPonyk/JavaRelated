"""Unit tests for the REAL MetricsService over FakeRedis / mocked Snowflake."""

import json
import time
from datetime import UTC, date, datetime

from app.config import get_settings
from app.db import snowflake_client
from app.services.metrics_service import MetricsService


def history_member(value: float, window_start_ms: int, window_ms: int = 500) -> str:
    return json.dumps(
        {
            "metric": "orders_per_second",
            "value": value,
            "window_start_ms": window_start_ms,
            "window_ms": window_ms,
        }
    )


async def test_list_current_reads_hot_hashes(fake_redis):
    await fake_redis.hset(
        "metric:orders_per_second",
        mapping={
            "metric": "orders_per_second",
            "value": "12.5",
            "window_start_ms": "1000",
            "window_ms": "500",
        },
    )
    service = MetricsService(client=fake_redis)

    current = await service.list_current()

    assert len(current) == 1
    assert current[0].value == 12.5
    assert await service.get_current("orders_per_second") is not None
    assert await service.get_current("revenue_per_second") is None


async def test_live_history_reads_zset_in_time_order(fake_redis):
    base = int(time.time() * 1000)
    await fake_redis.zadd(
        "metric:orders_per_second:history",
        {
            history_member(10.0, base - 1000): base - 1000,
            history_member(20.0, base - 500): base - 500,
            history_member(30.0, base): base,
        },
    )
    service = MetricsService(client=fake_redis)

    points = await service.history("orders_per_second", None, None, limit=10)

    assert [p.value for p in points] == [10.0, 20.0, 30.0]
    assert points[0].bucket_start < points[-1].bucket_start


async def test_live_history_respects_start_end_and_limit(fake_redis):
    base = int(time.time() * 1000)
    await fake_redis.zadd(
        "metric:orders_per_second:history",
        {history_member(float(i), base + i * 500): base + i * 500 for i in range(5)},
    )
    service = MetricsService(client=fake_redis)

    start = datetime.fromtimestamp((base + 500) / 1000, tz=UTC)
    end = datetime.fromtimestamp((base + 1500) / 1000, tz=UTC)
    points = await service.history("orders_per_second", start, end, limit=2)

    assert [p.value for p in points] == [1.0, 2.0]


async def test_daily_history_from_snowflake(monkeypatch, fake_redis):
    monkeypatch.setenv("HISTORY_SOURCE", "snowflake")
    get_settings.cache_clear()
    captured = {}

    def fake_fetch_all(sql, params=()):
        captured["sql"] = sql
        captured["params"] = list(params)
        return [(date(2026, 7, 1), 100.0), (date(2026, 7, 2), 140.0)]

    monkeypatch.setattr(snowflake_client, "fetch_all", fake_fetch_all)
    service = MetricsService(client=fake_redis)

    points = await service.history(
        "orders_count",
        datetime(2026, 7, 1, tzinfo=UTC),
        datetime(2026, 7, 2, tzinfo=UTC),
        limit=100,
        granularity="daily",
    )

    assert [p.value for p in points] == [100.0, 140.0]
    assert points[0].bucket_start == datetime(2026, 7, 1, tzinfo=UTC)
    assert "fct_business_metrics_daily" in captured["sql"]
    assert captured["params"][0] == "orders_count"


async def test_daily_history_falls_back_to_warmed_cache_without_snowflake(fake_redis):
    base = int(time.time() * 1000)
    await fake_redis.zadd(
        "metric:orders_per_second:daily",
        {json.dumps({"value": 9.0, "window_start_ms": base}): base},
    )
    service = MetricsService(client=fake_redis)  # HISTORY_SOURCE=redis (conftest default)

    points = await service.history("orders_per_second", None, None, 10, granularity="daily")

    assert [p.value for p in points] == [9.0]


async def test_stream_relays_pubsub_messages(fake_redis):
    await fake_redis.publish(
        "metrics.updates", json.dumps({"metric": "orders_per_second", "value": 5.0})
    )
    service = MetricsService(client=fake_redis)

    received = [message async for message in service.stream()]

    assert received == [{"metric": "orders_per_second", "value": 5.0}]
