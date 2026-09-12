"""Shared fixtures: hermetic app clients.

Pipelines and alerts run their REAL service/repository code over FakeRedis;
only the metrics service is stubbed at the router boundary (its real
implementation is covered separately in test_metrics_service.py).
"""

from __future__ import annotations

import pytest
from api_fakes import FakeRedis
from fastapi.testclient import TestClient

from app.config import get_settings
from app.main import create_app
from app.schemas.metric import CurrentMetric
from app.services.alert_service import AlertService, get_alert_service
from app.services.metrics_service import get_metrics_service
from app.services.pipeline_service import (
    PipelineService,
    RedisPipelineRepository,
    get_pipeline_service,
)


@pytest.fixture(autouse=True)
def base_env(monkeypatch):
    """Hermetic defaults: no Kafka consumer, no auth, Redis-backed stores."""
    monkeypatch.setenv("ALERTS_CONSUMER_ENABLED", "false")
    monkeypatch.setenv("AUTH_MODE", "none")
    monkeypatch.setenv("HISTORY_SOURCE", "redis")
    monkeypatch.setenv("PIPELINE_STORE", "redis")
    get_settings.cache_clear()
    yield
    get_settings.cache_clear()


class FakeMetricsService:
    """Stands in for the Redis/Snowflake-backed service at the router boundary."""

    async def list_current(self):
        return [
            CurrentMetric(metric="orders_per_second", value=42.0, window_start_ms=0, window_ms=500)
        ]

    async def get_current(self, metric: str):
        for item in await self.list_current():
            if item.metric == metric:
                return item
        return None

    async def history(self, metric, start, end, limit, granularity="live"):
        return []

    async def stream(self):
        yield {
            "metric": "orders_per_second",
            "value": 43.5,
            "window_start_ms": 500,
            "window_ms": 500,
        }


@pytest.fixture()
def fake_redis() -> FakeRedis:
    return FakeRedis()


def build_client(fake_redis: FakeRedis) -> TestClient:
    app = create_app()
    app.dependency_overrides[get_pipeline_service] = lambda: PipelineService(
        RedisPipelineRepository(client=fake_redis)
    )
    app.dependency_overrides[get_alert_service] = lambda: AlertService(client=fake_redis)
    app.dependency_overrides[get_metrics_service] = FakeMetricsService
    return TestClient(app)


@pytest.fixture()
def client(fake_redis) -> TestClient:
    return build_client(fake_redis)
