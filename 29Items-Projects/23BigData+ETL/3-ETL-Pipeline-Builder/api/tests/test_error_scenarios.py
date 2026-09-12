"""Degradation paths: hot store down → 503; corrupt stored data → skipped, not 500."""

import asyncio
import json
import time

from fastapi.testclient import TestClient
from redis.exceptions import RedisError

from app.main import create_app
from app.services.alert_service import AlertService
from app.services.metrics_service import MetricsService, get_metrics_service


class DownMetricsService:
    async def list_current(self):
        raise RedisError("connection refused")

    async def get_current(self, metric):
        raise RedisError("connection refused")

    async def history(self, *args, **kwargs):
        raise RedisError("connection refused")


def down_client() -> TestClient:
    app = create_app()
    app.dependency_overrides[get_metrics_service] = DownMetricsService
    return TestClient(app)


def test_hot_store_down_returns_503_not_500():
    client = down_client()
    assert client.get("/api/v1/metrics/current").status_code == 503
    assert client.get("/api/v1/metrics/orders_per_second/current").status_code == 503
    assert client.get("/api/v1/metrics/orders_per_second/history").status_code == 503
    # The ops probe must stay green — LB health is about the process, not redis.
    assert client.get("/healthz").status_code == 200


async def test_corrupt_hot_store_hash_degrades_to_missing_tile(fake_redis):
    await fake_redis.hset("metric:orders_per_second", mapping={"value": "not-a-float"})
    await fake_redis.hset(
        "metric:revenue_per_second",
        mapping={
            "metric": "revenue_per_second",
            "value": "5.0",
            "window_start_ms": "0",
            "window_ms": "500",
        },
    )
    service = MetricsService(client=fake_redis)

    current = await service.list_current()

    assert [m.metric for m in current] == ["revenue_per_second"]  # corrupt one skipped


async def test_corrupt_history_member_is_skipped(fake_redis):
    base = int(time.time() * 1000)
    await fake_redis.zadd(
        "metric:orders_per_second:history",
        {
            "{broken json": base - 500,
            json.dumps({"value": 7.0, "window_start_ms": base}): base,
        },
    )
    service = MetricsService(client=fake_redis)

    points = await service.history("orders_per_second", None, None, 10)

    assert [p.value for p in points] == [7.0]


def test_corrupt_stored_alert_is_skipped(client, fake_redis):
    service = AlertService(client=fake_redis)
    good = {
        "alert_id": "ok1",
        "metric": "orders_per_second",
        "value": 9.0,
        "score": 5.0,
        "severity": "warning",
        "message": "m",
        "triggered_at_ms": int(time.time() * 1000),
    }
    truncated = {"alert_id": "bad1", "triggered_at_ms": int(time.time() * 1000)}  # no metric/score
    asyncio.run(service.add(good))
    asyncio.run(service.add(truncated))

    body = client.get("/api/v1/alerts").json()

    assert [a["alert_id"] for a in body] == ["ok1"]


def test_pipelines_list_respects_limit(client):
    for i in range(4):
        payload = {
            "name": f"pipe-{i}",
            "schedule": "0 2 * * *",
            "source": "s3://lake/raw/",
            "target": "analytics.marts.fct_business_metrics_daily",
        }
        assert client.post("/api/v1/pipelines", json=payload).status_code == 201

    assert len(client.get("/api/v1/pipelines?limit=2").json()) == 2
    assert client.get("/api/v1/pipelines?limit=0").status_code == 422
