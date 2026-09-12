"""Integration tests for the prediction endpoints (real model + SQLite)."""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from conftest import make_prediction_payload
from fraud_detection.ml.features import FEATURE_COLUMNS

pytestmark = pytest.mark.integration


def test_scoring_happy_path_uses_trained_model(client: TestClient) -> None:
    response = client.post("/api/v1/predictions", json=make_prediction_payload(0))
    assert response.status_code == 200
    body = response.json()
    assert body["transaction_id"] == "txn-0"
    assert body["model_version"] == "1"
    assert body["variant"] in ("champion", "challenger")
    assert 0.0 <= body["fraud_probability"] <= 1.0
    assert body["is_fraud"] == (body["fraud_probability"] >= 0.5)


def test_prediction_is_persisted_with_features(client: TestClient) -> None:
    client.post("/api/v1/predictions", json=make_prediction_payload(1))
    response = client.get("/api/v1/predictions/txn-1")
    assert response.status_code == 200
    record = response.json()
    assert record["account_id"] == "acct-1"
    assert set(FEATURE_COLUMNS) <= set(record["features"])
    assert record["created_at"] is not None


def test_list_supports_pagination_and_account_filter(client: TestClient) -> None:
    for i in range(6):
        payload = make_prediction_payload(i, account_id="acct-a" if i < 4 else "acct-b")
        assert client.post("/api/v1/predictions", json=payload).status_code == 200

    body = client.get("/api/v1/predictions?limit=3&offset=0").json()
    assert body["total"] == 6 and len(body["items"]) == 3

    filtered = client.get("/api/v1/predictions?account_id=acct-b").json()
    assert filtered["total"] == 2
    assert all(item["account_id"] == "acct-b" for item in filtered["items"])


def test_missing_prediction_returns_problem_json(client: TestClient) -> None:
    response = client.get("/api/v1/predictions/nope")
    assert response.status_code == 404
    assert response.headers["content-type"].startswith("application/problem+json")
    body = response.json()
    assert body["status"] == 404 and body["title"] == "Not Found"
    assert "nope" in body["detail"]


def test_invalid_payload_returns_problem_json_422(client: TestClient) -> None:
    response = client.post("/api/v1/predictions", json=make_prediction_payload(9, amount=-10))
    assert response.status_code == 422
    assert response.headers["content-type"].startswith("application/problem+json")
    body = response.json()
    assert body["status"] == 422
    assert isinstance(body["errors"], list) and body["errors"]
