"""API tests: full alert-rule CRUD round-trip with an isolated in-memory repo per test.

Demonstrates the testing pattern for every future endpoint: TestClient + dependency override,
no network, no mocks of our own code.
"""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from log_analytics.api.deps import get_alert_rule_service
from log_analytics.api.main import app
from log_analytics.api.services.alert_rule_service import (
    AlertRuleService,
    InMemoryAlertRuleRepository,
)

VALID_RULE = {
    "name": "High error ratio (test)",
    "metric": "error_ratio",
    "op": "gt",
    "threshold": 0.05,
    "window": "5m",
    "severity": "warning",
    "channels": ["log", "slack"],
}


@pytest.fixture()
def client() -> TestClient:
    service = AlertRuleService(InMemoryAlertRuleRepository())  # fresh state per test
    app.dependency_overrides[get_alert_rule_service] = lambda: service
    try:
        yield TestClient(app)
    finally:
        app.dependency_overrides.clear()


def test_healthz(client: TestClient) -> None:
    resp = client.get("/healthz")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok"}


def test_create_and_get_roundtrip(client: TestClient) -> None:
    created = client.post("/api/v1/alert-rules", json=VALID_RULE)
    assert created.status_code == 201
    body = created.json()
    assert body["id"]
    assert body["created_at"]
    assert body["threshold"] == 0.05

    fetched = client.get(f"/api/v1/alert-rules/{body['id']}")
    assert fetched.status_code == 200
    assert fetched.json()["name"] == VALID_RULE["name"]


def test_list_with_pagination(client: TestClient) -> None:
    for i in range(3):
        payload = {**VALID_RULE, "name": f"rule number {i}"}
        assert client.post("/api/v1/alert-rules", json=payload).status_code == 201

    resp = client.get("/api/v1/alert-rules", params={"limit": 2, "offset": 0})
    assert resp.status_code == 200
    body = resp.json()
    assert body["total"] == 3
    assert len(body["items"]) == 2


def test_partial_update(client: TestClient) -> None:
    rule_id = client.post("/api/v1/alert-rules", json=VALID_RULE).json()["id"]

    resp = client.put(f"/api/v1/alert-rules/{rule_id}", json={"threshold": 0.2, "enabled": False})
    assert resp.status_code == 200
    body = resp.json()
    assert body["threshold"] == 0.2
    assert body["enabled"] is False
    assert body["name"] == VALID_RULE["name"]  # untouched fields survive


def test_delete_then_404(client: TestClient) -> None:
    rule_id = client.post("/api/v1/alert-rules", json=VALID_RULE).json()["id"]
    assert client.delete(f"/api/v1/alert-rules/{rule_id}").status_code == 204
    assert client.get(f"/api/v1/alert-rules/{rule_id}").status_code == 404


def test_duplicate_name_maps_to_409(client: TestClient) -> None:
    assert client.post("/api/v1/alert-rules", json=VALID_RULE).status_code == 201
    assert client.post("/api/v1/alert-rules", json=VALID_RULE).status_code == 409


def test_rename_to_existing_name_maps_to_409(client: TestClient) -> None:
    client.post("/api/v1/alert-rules", json=VALID_RULE)
    other = client.post("/api/v1/alert-rules", json={**VALID_RULE, "name": "another rule"}).json()
    resp = client.put(f"/api/v1/alert-rules/{other['id']}", json={"name": VALID_RULE["name"]})
    assert resp.status_code == 409


def test_validation_rejects_bad_window(client: TestClient) -> None:
    resp = client.post("/api/v1/alert-rules", json={**VALID_RULE, "window": "5 minutes"})
    assert resp.status_code == 422


def test_validation_rejects_unknown_metric_and_channel(client: TestClient) -> None:
    assert (
        client.post("/api/v1/alert-rules", json={**VALID_RULE, "metric": "cpu"}).status_code == 422
    )
    assert (
        client.post("/api/v1/alert-rules", json={**VALID_RULE, "channels": ["sms"]}).status_code
        == 422
    )


def test_metacharacter_rule_ids_rejected(client: TestClient) -> None:
    """Ids that could smuggle URL structure into storage paths never reach the service.

    Single-segment junk fails the path-param pattern (422); traversal/encoded-slash ids
    don't even match the route after normalization (404). Both are rejections.
    """
    for bad_id in ("a*b", "a b", "..", "a%2Fb", "x" * 65):
        assert client.get(f"/api/v1/alert-rules/{bad_id}").status_code in (404, 422)
        assert client.delete(f"/api/v1/alert-rules/{bad_id}").status_code in (404, 422)
    assert client.get("/api/v1/alert-rules/a*b").status_code == 422  # pattern, not routing


def test_list_offset_beyond_search_window_rejected_422(client: TestClient) -> None:
    assert client.get("/api/v1/alert-rules", params={"offset": 10_001}).status_code == 422


def test_large_responses_are_gzip_compressed(client: TestClient) -> None:
    resp = client.get("/openapi.json", headers={"Accept-Encoding": "gzip"})
    assert resp.status_code == 200
    assert resp.headers.get("content-encoding") == "gzip"
