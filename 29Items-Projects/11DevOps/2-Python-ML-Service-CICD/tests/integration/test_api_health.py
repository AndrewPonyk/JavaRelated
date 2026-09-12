"""Integration tests for health probes and the Prometheus metrics endpoint."""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from conftest import make_prediction_payload

pytestmark = pytest.mark.integration


def test_liveness_returns_200(client: TestClient) -> None:
    response = client.get("/health/live")
    assert response.status_code == 200
    assert response.json() == {"status": "alive"}


def test_readiness_reports_components(client: TestClient) -> None:
    response = client.get("/health/ready")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ready"
    assert body["components"]["database"] == "up"
    assert body["components"]["model"] == "local"


def test_metrics_expose_canonical_names(client: TestClient) -> None:
    assert client.post("/api/v1/predictions", json=make_prediction_payload(0)).status_code == 200
    text = client.get("/metrics").text
    for metric in (
        "fraud_predictions_total",
        "fraud_prediction_latency_seconds",
        "http_requests_total",
        "http_request_duration_seconds",
    ):
        assert metric in text, f"missing metric: {metric}"


def test_request_id_header_roundtrip(client: TestClient) -> None:
    response = client.get("/health/live", headers={"X-Request-ID": "trace-me-123"})
    assert response.headers["X-Request-ID"] == "trace-me-123"
    generated = client.get("/health/live")
    assert generated.headers["X-Request-ID"]
