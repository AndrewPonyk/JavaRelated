"""Integration tests for retraining jobs and drift endpoints."""

from __future__ import annotations

import time

import pytest
from fastapi.testclient import TestClient

from conftest import make_prediction_payload
from fraud_detection.api import deps
from fraud_detection.core.config import get_settings
from fraud_detection.ml.features import FEATURE_COLUMNS

pytestmark = pytest.mark.integration


def _poll_job(client: TestClient, job_id: str, timeout: float = 120.0) -> dict:
    deadline = time.time() + timeout
    while time.time() < deadline:
        body = client.get(f"/api/v1/admin/retrain/{job_id}").json()
        if body["status"] in ("succeeded", "failed"):
            return body
        time.sleep(0.2)
    raise TimeoutError(f"retraining job {job_id} did not finish")


def test_manual_retraining_end_to_end(client: TestClient) -> None:
    response = client.post("/api/v1/admin/retrain", json={"reason": "integration-test"})
    assert response.status_code == 202
    body = response.json()
    assert body["status"] in ("queued", "running")

    final = _poll_job(client, body["job_id"])
    assert final["status"] == "succeeded", final["detail"]
    new_version = final["result"]["version"]
    assert new_version == "2"

    catalog = client.get("/api/v1/models").json()
    assert catalog["aliases"]["challenger"] == new_version
    versions = {item["version"]: item for item in catalog["items"]}
    assert versions[new_version]["stage"] == "challenger"


def test_unknown_retraining_job_is_404(client: TestClient) -> None:
    response = client.get("/api/v1/admin/retrain/no-such-job")
    assert response.status_code == 404
    assert response.headers["content-type"].startswith("application/problem+json")


def test_drift_reports_insufficient_data_on_empty_db(client: TestClient) -> None:
    body = client.get("/api/v1/admin/drift").json()
    assert body["status"] == "insufficient_data"
    assert body["retraining_triggered"] is False


def test_drift_evaluation_over_live_traffic(
    client: TestClient, monkeypatch: pytest.MonkeyPatch
) -> None:
    # Keep this test hermetic: no background retraining threads.
    monkeypatch.setenv("FRAUD_AUTO_RETRAIN_ON_DRIFT", "false")
    get_settings.cache_clear()
    deps.reset_singletons()

    for i in range(15):
        assert (
            client.post("/api/v1/predictions", json=make_prediction_payload(i)).status_code == 200
        )

    body = client.get("/api/v1/admin/drift").json()
    assert body["status"] in ("ok", "drift_detected")
    assert body["evaluated_rows"] == 15
    assert {feature["feature_name"] for feature in body["features"]} == set(FEATURE_COLUMNS)
    assert body["retraining_triggered"] is False

    reports = client.get("/api/v1/admin/drift/reports").json()["items"]
    assert len(reports) == len(FEATURE_COLUMNS)
    assert all("psi_score" in report for report in reports)
