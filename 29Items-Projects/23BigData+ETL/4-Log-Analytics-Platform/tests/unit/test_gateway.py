"""Ingestion gateway: validation, auth, gzip, rate limiting, Kafka-failure mapping.

The Kafka producer is replaced with a module-level fake (the lifespan doesn't run
outside a TestClient context manager, so `gateway._producer` is ours to set).
"""

from __future__ import annotations

import gzip
import json

import pytest
from aiokafka.errors import KafkaConnectionError
from fastapi.testclient import TestClient

import log_analytics.ingestion.gateway as gateway
from log_analytics.common.config import Settings, get_settings
from log_analytics.common.models import LogEvent
from log_analytics.ingestion.gateway import RateLimiter, app


class FakeProducer:
    def __init__(self, fail: bool = False) -> None:
        self.sent: list[LogEvent] = []
        self._fail = fail

    async def send_batch(self, events: list[LogEvent]) -> int:
        if self._fail:
            raise KafkaConnectionError("broker down")
        self.sent.extend(events)
        return len(events)

    async def ready(self) -> bool:
        return not self._fail


@pytest.fixture()
def client(monkeypatch) -> TestClient:
    fake = FakeProducer()
    monkeypatch.setattr(gateway, "_producer", fake)
    monkeypatch.setattr(gateway, "_limiter", None)
    app.dependency_overrides[get_settings] = lambda: Settings(_env_file=None)
    try:
        test_client = TestClient(app)
        test_client.fake_producer = fake  # type: ignore[attr-defined]
        yield test_client
    finally:
        app.dependency_overrides.clear()


VALID_EVENT = {
    "timestamp": "2026-07-01T12:00:00Z",
    "service": "checkout",
    "level": "warning",
    "message": "slow query",
}


def test_batch_accepted_and_produced(client: TestClient) -> None:
    resp = client.post("/v1/logs", json=[VALID_EVENT, VALID_EVENT])
    assert resp.status_code == 202
    assert resp.json() == {"accepted": 2, "rejected": 0, "errors": []}
    assert len(client.fake_producer.sent) == 2  # type: ignore[attr-defined]
    assert client.fake_producer.sent[0].level.value == "WARN"  # type: ignore[attr-defined]


def test_partial_rejection_keeps_batch_flowing(client: TestClient) -> None:
    bad = {"service": "x"}  # no timestamp/message
    resp = client.post("/v1/logs", json=[VALID_EVENT, bad, "not-an-object"])
    assert resp.status_code == 202
    body = resp.json()
    assert body["accepted"] == 1
    assert body["rejected"] == 2
    assert len(body["errors"]) == 2


def test_alias_field_names_are_coerced(client: TestClient) -> None:
    aliased = {"@timestamp": "2026-07-01T12:00:00Z", "app": "checkout", "msg": "hi"}
    resp = client.post("/v1/logs", json=[aliased])
    assert resp.json()["accepted"] == 1


def test_gzip_body(client: TestClient) -> None:
    payload = gzip.compress(json.dumps([VALID_EVENT]).encode())
    resp = client.post(
        "/v1/logs",
        content=payload,
        headers={"Content-Encoding": "gzip", "Content-Type": "application/json"},
    )
    assert resp.status_code == 202
    assert resp.json()["accepted"] == 1


def test_invalid_gzip_and_invalid_json(client: TestClient) -> None:
    resp = client.post("/v1/logs", content=b"garbage", headers={"Content-Encoding": "gzip"})
    assert resp.status_code == 400
    resp = client.post(
        "/v1/logs", content=b"{not json", headers={"Content-Type": "application/json"}
    )
    assert resp.status_code == 400
    resp = client.post("/v1/logs", json={"an": "object"})
    assert resp.status_code == 400


def test_gzip_bomb_rejected_413(client: TestClient) -> None:
    """A tiny compressed body inflating past the cap must die at the cap, not at OOM."""
    bomb = gzip.compress(b"[" + b" " * (40 * 1024 * 1024))  # 40 MB of spaces → ~40 KB gzip
    assert len(bomb) < 1024 * 1024  # premise: it really is a bomb, not just a big body
    resp = client.post(
        "/v1/logs",
        content=bomb,
        headers={"Content-Encoding": "gzip", "Content-Type": "application/json"},
    )
    assert resp.status_code == 413
    assert "decompressed" in resp.json()["detail"]


def test_oversize_raw_body_413(client: TestClient) -> None:
    resp = client.post(
        "/v1/logs",
        content=b"[" + b" " * (33 * 1024 * 1024),  # > 32 MB cap, plain body
        headers={"Content-Type": "application/json"},
    )
    assert resp.status_code == 413


def test_oversize_batch_413(client: TestClient) -> None:
    app.dependency_overrides[get_settings] = lambda: Settings(_env_file=None, gateway_max_batch=2)
    resp = client.post("/v1/logs", json=[VALID_EVENT] * 3)
    assert resp.status_code == 413


def test_api_key_required_when_configured(client: TestClient) -> None:
    app.dependency_overrides[get_settings] = lambda: Settings(
        _env_file=None, gateway_api_keys="secret-key"
    )
    assert client.post("/v1/logs", json=[VALID_EVENT]).status_code == 401
    wrong = client.post("/v1/logs", json=[VALID_EVENT], headers={"X-API-Key": "secret-kez"})
    assert wrong.status_code == 401
    resp = client.post("/v1/logs", json=[VALID_EVENT], headers={"X-API-Key": "secret-key"})
    assert resp.status_code == 202


def test_rate_limit_429(client: TestClient) -> None:
    app.dependency_overrides[get_settings] = lambda: Settings(
        _env_file=None, gateway_rate_limit_rps=1.0, gateway_rate_limit_burst=5
    )
    assert client.post("/v1/logs", json=[VALID_EVENT] * 3).status_code == 202
    assert client.post("/v1/logs", json=[VALID_EVENT] * 3).status_code == 429  # bucket drained


def test_kafka_failure_maps_to_503(client: TestClient, monkeypatch) -> None:
    monkeypatch.setattr(gateway, "_producer", FakeProducer(fail=True))
    resp = client.post("/v1/logs", json=[VALID_EVENT])
    assert resp.status_code == 503


def test_readyz_reflects_producer_state(client: TestClient, monkeypatch) -> None:
    assert client.get("/readyz").status_code == 200
    monkeypatch.setattr(gateway, "_producer", FakeProducer(fail=True))
    assert client.get("/readyz").status_code == 503


class TestRateLimiter:
    def test_burst_then_deny(self) -> None:
        limiter = RateLimiter(rate_per_second=0.0001, burst=10)
        assert limiter.try_consume("caller", 10) is True
        assert limiter.try_consume("caller", 1) is False

    def test_callers_are_independent(self) -> None:
        limiter = RateLimiter(rate_per_second=0.0001, burst=5)
        assert limiter.try_consume("a", 5) is True
        assert limiter.try_consume("b", 5) is True

    def test_refill_over_time(self, monkeypatch) -> None:
        now = [1000.0]
        monkeypatch.setattr("log_analytics.ingestion.gateway.time.monotonic", lambda: now[0])
        limiter = RateLimiter(rate_per_second=10.0, burst=10)
        assert limiter.try_consume("a", 10) is True
        assert limiter.try_consume("a", 5) is False
        now[0] += 0.5  # +5 tokens
        assert limiter.try_consume("a", 5) is True
