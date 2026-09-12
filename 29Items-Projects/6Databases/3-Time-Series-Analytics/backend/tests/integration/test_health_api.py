"""API smoke tests — degraded-mode contract (docs/ARCHITECTURE.md §2.6):
liveness green, readiness 503 with per-dependency detail, data endpoints 503
in the unified error shape, auth always enforced."""

from datetime import UTC, datetime


def test_liveness_is_green_without_backends(offline_client):
    resp = offline_client.get("/api/v1/health/live")
    assert resp.status_code == 200
    assert resp.json()["status"] == "ok"


def test_readiness_reports_down_dependencies(offline_client):
    resp = offline_client.get("/api/v1/health/ready")
    assert resp.status_code == 503
    body = resp.json()
    assert body["status"] == "degraded"
    assert body["checks"]["cassandra"] == "down"
    assert body["checks"]["redis"] == "down"
    assert body["checks"]["influxdb"] == "down"


def test_ingest_rejects_missing_api_key(offline_client):
    batch = {
        "points": [
            {
                "device_id": "dev-1",
                "metric": "temperature",
                "ts": datetime.now(UTC).isoformat(),
                "value": 21.5,
            }
        ]
    }
    resp = offline_client.post("/api/v1/ingest", json=batch)
    assert resp.status_code == 401
    assert resp.json()["error"]["code"] == "unauthorized"


def test_query_requires_auth(offline_client):
    resp = offline_client.get("/api/v1/devices/dev-1/metrics/temperature")
    assert resp.status_code == 401


def test_events_stream_requires_auth(offline_client):
    resp = offline_client.get("/api/v1/events/anomalies", params={"device_id": "dev-1"})
    assert resp.status_code == 401


def test_backend_unavailable_maps_to_503_error_shape(offline_client, monkeypatch):
    from app.core.config import settings

    monkeypatch.setattr(settings, "device_api_key", "test-gateway-key")
    batch = {
        "points": [
            {
                "device_id": "dev-1",
                "metric": "temperature",
                "ts": datetime.now(UTC).isoformat(),
                "value": 21.5,
            }
        ]
    }
    resp = offline_client.post(
        "/api/v1/ingest", json=batch, headers={"X-API-Key": "test-gateway-key"}
    )
    assert resp.status_code == 503
    error = resp.json()["error"]
    assert error["code"] == "backend_unavailable"
    assert error["request_id"]


def test_validation_errors_use_unified_shape(offline_client, monkeypatch):
    from app.core.config import settings

    monkeypatch.setattr(settings, "device_api_key", "test-gateway-key")
    resp = offline_client.post(
        "/api/v1/ingest",
        json={"points": [{"device_id": "", "metric": "BAD METRIC", "ts": "x", "value": 1}]},
        headers={"X-API-Key": "test-gateway-key"},
    )
    assert resp.status_code == 422
    error = resp.json()["error"]
    assert error["code"] == "validation_error"
    assert isinstance(error["detail"], list) and error["detail"]


def test_responses_carry_request_id_header(offline_client):
    resp = offline_client.get("/api/v1/health/live")
    assert resp.headers.get("X-Request-ID")
