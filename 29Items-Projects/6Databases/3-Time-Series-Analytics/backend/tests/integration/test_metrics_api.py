"""Metric query endpoints: routing, decimation, catalog, live, anomalies."""

from datetime import UTC, datetime, timedelta

import pytest

from app.schemas.anomaly import Anomaly
from app.schemas.metric import LiveAggregate, SeriesPoint

NOW = datetime.now(UTC)


def _series(n: int, start: datetime) -> list[SeriesPoint]:
    return [SeriesPoint(ts=start + timedelta(minutes=i), value=float(i)) for i in range(n)]


@pytest.fixture()
def fake_queries(monkeypatch):
    calls = {"raw": 0, "rollup": 0}

    async def fake_raw(device_id, metric, start, end):
        calls["raw"] += 1
        return _series(10, start)

    async def fake_rollup(device_id, metric, start, end):
        calls["rollup"] += 1
        return _series(10, start)

    async def fake_catalog(device_id):
        return ["humidity", "temperature"]

    async def fake_anomalies(device_id, start, end):
        return [
            Anomaly(
                device_id=device_id,
                metric="temperature",
                ts=NOW,
                value=99.0,
                expected=20.0,
                lower=10.0,
                upper=30.0,
                score=5.0,
                method="zscore",
            )
        ] * 3

    monkeypatch.setattr("app.repositories.metrics.query_raw", fake_raw)
    monkeypatch.setattr("app.repositories.metrics.query_rollup_1h", fake_rollup)
    monkeypatch.setattr("app.repositories.series_catalog.metrics_for", fake_catalog)
    monkeypatch.setattr("app.repositories.anomalies.query", fake_anomalies)
    return calls


def test_short_range_uses_raw(client, viewer_headers, fake_queries):
    resp = client.get(
        "/api/v1/devices/dev-a/metrics/temperature",
        params={"start": (NOW - timedelta(hours=6)).isoformat(), "end": NOW.isoformat()},
        headers=viewer_headers,
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["source"] == "raw" and not body["decimated"]
    assert fake_queries == {"raw": 1, "rollup": 0}


def test_wide_range_uses_rollups(client, viewer_headers, fake_queries):
    resp = client.get(
        "/api/v1/devices/dev-a/metrics/temperature",
        params={"start": (NOW - timedelta(days=7)).isoformat(), "end": NOW.isoformat()},
        headers=viewer_headers,
    )
    assert resp.json()["source"] == "rollup_1h"
    assert fake_queries == {"raw": 0, "rollup": 1}


def test_default_range_is_last_hour_raw(client, viewer_headers, fake_queries):
    resp = client.get("/api/v1/devices/dev-a/metrics/temperature", headers=viewer_headers)
    assert resp.status_code == 200
    assert resp.json()["source"] == "raw"


def test_inverted_range_is_422(client, viewer_headers, fake_queries):
    resp = client.get(
        "/api/v1/devices/dev-a/metrics/temperature",
        params={"start": NOW.isoformat(), "end": (NOW - timedelta(hours=1)).isoformat()},
        headers=viewer_headers,
    )
    assert resp.status_code == 422


def test_oversized_response_is_decimated(client, viewer_headers, fake_queries, monkeypatch):
    monkeypatch.setattr("app.core.config.settings.max_series_points", 50)

    async def big_raw(device_id, metric, start, end):
        return _series(500, start)

    monkeypatch.setattr("app.repositories.metrics.query_raw", big_raw)
    resp = client.get("/api/v1/devices/dev-a/metrics/temperature", headers=viewer_headers)
    body = resp.json()
    assert body["decimated"] is True
    assert len(body["points"]) <= 51


def test_metric_catalog(client, viewer_headers, fake_queries):
    resp = client.get("/api/v1/devices/dev-a/metrics", headers=viewer_headers)
    assert resp.status_code == 200
    assert resp.json() == ["humidity", "temperature"]


def test_live_aggregate(client, viewer_headers, monkeypatch):
    async def fake_live(device_id, metric):
        return LiveAggregate(
            device_id=device_id,
            metric=metric,
            window_start=NOW,
            count=4,
            sum=88.0,
            avg=22.0,
            min=20.0,
            max=24.0,
            latest=24.0,
        )

    monkeypatch.setattr("app.services.aggregation.get_live", fake_live)
    resp = client.get("/api/v1/devices/dev-a/metrics/temperature/live", headers=viewer_headers)
    assert resp.status_code == 200
    body = resp.json()
    assert body["count"] == 4 and body["min"] == 20.0 and body["max"] == 24.0


def test_anomaly_feed_with_limit(client, viewer_headers, fake_queries):
    resp = client.get(
        "/api/v1/devices/dev-a/anomalies", params={"limit": 2}, headers=viewer_headers
    )
    assert resp.status_code == 200
    assert len(resp.json()) == 2
    assert resp.json()[0]["method"] == "zscore"
