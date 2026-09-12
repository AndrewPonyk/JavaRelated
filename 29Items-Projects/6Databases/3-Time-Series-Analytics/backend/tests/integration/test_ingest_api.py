"""Ingest pipeline: auth planes, policy rejection, rate limiting, side effects."""

from datetime import UTC, datetime, timedelta

import pytest

from app.services import ingestion

GATEWAY_KEY = "test-gateway-key"
NOW = datetime.now(UTC)


def _batch(*specs) -> dict:
    """specs: (device_id, value) or (device_id, value, ts)"""
    points = []
    for spec in specs:
        device_id, value, *rest = spec
        ts = rest[0] if rest else NOW
        points.append(
            {"device_id": device_id, "metric": "temperature", "ts": ts.isoformat(), "value": value}
        )
    return {"points": points}


@pytest.fixture()
def pipeline(monkeypatch):
    """Fake everything below the ingestion service; capture what it writes."""
    monkeypatch.setattr("app.core.config.settings.device_api_key", GATEWAY_KEY)
    ingestion._known_series.clear()

    written: list = []
    catalog: list = []
    live: list = []
    enabled = {"dev-a": True, "dev-b": True, "dev-off": False}

    async def fake_insert(points):
        written.extend(points)

    async def fake_catalog(pairs):
        catalog.extend(sorted(pairs))

    async def fake_record(point):
        live.append(point)

    async def fake_allowed(device_id):
        return enabled.get(device_id, False)

    async def fake_verify(device_id, secret):
        return device_id in enabled and secret == "valid-secret"

    monkeypatch.setattr("app.repositories.metrics.insert_points", fake_insert)
    monkeypatch.setattr("app.repositories.series_catalog.register_many", fake_catalog)
    monkeypatch.setattr("app.services.aggregation.record_point", fake_record)
    monkeypatch.setattr("app.services.device_registry.ingest_allowed", fake_allowed)
    monkeypatch.setattr("app.services.device_registry.verify_device_secret", fake_verify)
    yield {"written": written, "catalog": catalog, "live": live}
    ingestion._known_series.clear()


def test_gateway_key_accepts_multi_device_batch(client, pipeline):
    resp = client.post(
        "/api/v1/ingest",
        json=_batch(("dev-a", 20.0), ("dev-b", 30.0)),
        headers={"X-API-Key": GATEWAY_KEY},
    )
    assert resp.status_code == 202
    assert resp.json() == {"accepted": 2, "rejected": 0}
    assert len(pipeline["written"]) == 2
    assert pipeline["catalog"] == [("dev-a", "temperature"), ("dev-b", "temperature")]
    assert len(pipeline["live"]) == 2


def test_unknown_and_disabled_devices_are_rejected(client, pipeline):
    resp = client.post(
        "/api/v1/ingest",
        json=_batch(("dev-a", 20.0), ("dev-off", 1.0), ("dev-ghost", 2.0)),
        headers={"X-API-Key": GATEWAY_KEY},
    )
    assert resp.status_code == 202
    assert resp.json() == {"accepted": 1, "rejected": 2}
    assert [p.device_id for p in pipeline["written"]] == ["dev-a"]


def test_stale_and_future_points_are_rejected(client, pipeline):
    resp = client.post(
        "/api/v1/ingest",
        json=_batch(
            ("dev-a", 20.0),
            ("dev-a", 21.0, NOW - timedelta(hours=48)),
            ("dev-a", 22.0, NOW + timedelta(hours=2)),
        ),
        headers={"X-API-Key": GATEWAY_KEY},
    )
    assert resp.json() == {"accepted": 1, "rejected": 2}


def test_device_key_writes_its_own_data(client, pipeline):
    resp = client.post(
        "/api/v1/ingest",
        json=_batch(("dev-a", 20.0)),
        headers={"X-API-Key": "dev-a.valid-secret"},
    )
    assert resp.status_code == 202
    assert resp.json()["accepted"] == 1


def test_device_key_cannot_write_other_devices(client, pipeline):
    resp = client.post(
        "/api/v1/ingest",
        json=_batch(("dev-a", 20.0), ("dev-b", 30.0)),
        headers={"X-API-Key": "dev-a.valid-secret"},
    )
    assert resp.status_code == 403
    assert resp.json()["error"]["code"] == "forbidden"
    assert pipeline["written"] == []


def test_invalid_keys_are_401(client, pipeline):
    for bad in ("dev-a.wrong-secret", "not-a-key", ""):
        resp = client.post(
            "/api/v1/ingest", json=_batch(("dev-a", 20.0)), headers={"X-API-Key": bad}
        )
        assert resp.status_code == 401, bad


def test_oversized_batch_is_413(client, pipeline, monkeypatch):
    monkeypatch.setattr("app.core.config.settings.max_ingest_batch_size", 2)
    resp = client.post(
        "/api/v1/ingest",
        json=_batch(("dev-a", 1.0), ("dev-a", 2.0), ("dev-a", 3.0)),
        headers={"X-API-Key": GATEWAY_KEY},
    )
    assert resp.status_code == 413


def test_rate_limit_returns_429_with_retry_after(client, pipeline, monkeypatch):
    monkeypatch.setattr("app.core.config.settings.ingest_rate_limit_per_minute", 3)

    class FakeRateRedis:
        def __init__(self):
            self.counts: dict[str, int] = {}

        async def incrby(self, key, amount):
            self.counts[key] = self.counts.get(key, 0) + amount
            return self.counts[key]

        async def expire(self, key, ttl):
            return True

    fake = FakeRateRedis()
    monkeypatch.setattr("app.db.redis.get_client", lambda: fake)

    ok = client.post(
        "/api/v1/ingest",
        json=_batch(("dev-a", 1.0), ("dev-a", 2.0)),
        headers={"X-API-Key": GATEWAY_KEY},
    )
    assert ok.status_code == 202

    throttled = client.post(
        "/api/v1/ingest",
        json=_batch(("dev-a", 3.0), ("dev-a", 4.0)),
        headers={"X-API-Key": GATEWAY_KEY},
    )
    assert throttled.status_code == 429
    assert throttled.headers.get("Retry-After") == "60"


def test_live_aggregate_failure_does_not_fail_ingest(client, pipeline, monkeypatch):
    async def broken_record(point):
        raise RuntimeError("redis hiccup")

    monkeypatch.setattr("app.services.aggregation.record_point", broken_record)
    resp = client.post(
        "/api/v1/ingest", json=_batch(("dev-a", 20.0)), headers={"X-API-Key": GATEWAY_KEY}
    )
    assert resp.status_code == 202
    assert resp.json()["accepted"] == 1  # Cassandra write is the transaction
