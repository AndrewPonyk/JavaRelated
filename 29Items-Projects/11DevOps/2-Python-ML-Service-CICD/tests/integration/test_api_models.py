"""Integration tests for the model catalog, A/B config and promotion routes."""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from fraud_detection.ml.train import train_pipeline

pytestmark = pytest.mark.integration


def _train_second_version(settings) -> str:
    summary = train_pipeline(model_dir=settings.model_dir, n_samples=3000, seed=7)
    assert summary["gates_passed"] is True
    return str(summary["version"])


def test_list_models_shows_local_catalog(client: TestClient) -> None:
    body = client.get("/api/v1/models").json()
    assert body["aliases"]["champion"] == "1"
    assert body["items"][0]["version"] == "1"
    assert body["items"][0]["source"] == "local"
    assert body["items"][0]["auc"] is not None


def test_get_single_version_and_404(client: TestClient) -> None:
    assert client.get("/api/v1/models/1").json()["version"] == "1"
    missing = client.get("/api/v1/models/99")
    assert missing.status_code == 404
    assert missing.headers["content-type"].startswith("application/problem+json")


def test_ab_config_roundtrip_and_validation(client: TestClient) -> None:
    initial = client.get("/api/v1/models/ab-config").json()
    assert initial["enabled"] is True and initial["traffic_split"] == 10
    assert initial["champion_version"] == "1"

    updated = client.put("/api/v1/models/ab-config", json={"traffic_split": 55}).json()
    assert updated["traffic_split"] == 55 and updated["enabled"] is True

    updated = client.put("/api/v1/models/ab-config", json={"enabled": False}).json()
    assert updated["enabled"] is False and updated["traffic_split"] == 55

    invalid = client.put("/api/v1/models/ab-config", json={"traffic_split": 150})
    assert invalid.status_code == 422


def test_promote_challenger_to_champion(client: TestClient, settings) -> None:
    version = _train_second_version(settings)
    response = client.post("/api/v1/models/promote", json={"version": version, "alias": "champion"})
    assert response.status_code == 200
    assert response.json() == {"version": version, "alias": "champion", "previous": "1"}

    body = client.get("/api/v1/models").json()
    assert body["aliases"]["champion"] == version

    # Champion-routed traffic now serves the promoted version.
    client.put("/api/v1/models/ab-config", json={"enabled": False})
    scored = client.post(
        "/api/v1/predictions",
        json={
            "transaction_id": "txn-after-promote",
            "account_id": "acct-x",
            "amount": 42.0,
            "merchant_category": "grocery",
            "timestamp": "2026-07-12T10:00:00Z",
        },
    ).json()
    assert scored["model_version"] == version


def test_promote_unknown_version_is_404(client: TestClient) -> None:
    response = client.post("/api/v1/models/promote", json={"version": "77"})
    assert response.status_code == 404


def test_delete_rules(client: TestClient, settings) -> None:
    version = _train_second_version(settings)
    client.post("/api/v1/models/promote", json={"version": version, "alias": "champion"})

    assert client.delete("/api/v1/models/1").status_code == 204
    assert client.get("/api/v1/models/1").status_code == 404

    conflict = client.delete(f"/api/v1/models/{version}")
    assert conflict.status_code == 409
    assert conflict.headers["content-type"].startswith("application/problem+json")

    assert client.delete("/api/v1/models/55").status_code == 404
