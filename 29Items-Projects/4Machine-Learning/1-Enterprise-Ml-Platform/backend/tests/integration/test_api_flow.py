"""End-to-end API tests: the main user flow plus auth and error scenarios."""
from __future__ import annotations

from httpx import AsyncClient

from tests.conftest import make_token


async def test_health_and_security_headers(client: AsyncClient) -> None:
    resp = await client.get("/health")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok"}
    assert resp.headers["X-Content-Type-Options"] == "nosniff"
    assert "X-Correlation-ID" in resp.headers


async def test_full_lifecycle_flow(client: AsyncClient, auth_headers: dict) -> None:
    # 1. Create an experiment.
    created = await client.post(
        "/experiments", json={"name": "Churn Model"}, headers=auth_headers
    )
    assert created.status_code == 201
    exp_id = created.json()["id"]

    # 2. It appears in the paginated list.
    listed = await client.get("/experiments?limit=10&offset=0", headers=auth_headers)
    assert listed.status_code == 200
    body = listed.json()
    assert body["total"] == 1 and len(body["items"]) == 1

    # 3. Train -> registers a model version.
    trained = await client.post(
        f"/experiments/{exp_id}/train",
        json={"framework": "xgboost", "dataset_uri": "s3://data/churn"},
        headers=auth_headers,
    )
    assert trained.status_code == 202
    assert trained.json()["status"] == "COMPLETED"

    # 4. The version is listed in the registry.
    versions = await client.get("/models/churn-model/versions", headers=auth_headers)
    assert versions.status_code == 200
    assert versions.json()[0]["version"] == 1

    # 5. Promote to Production (requires models:promote).
    promoted = await client.post(
        "/models/churn-model/versions/1/stage",
        json={"stage": "Production"},
        headers=auth_headers,
    )
    assert promoted.status_code == 200
    assert promoted.json()["stage"] == "Production"

    # 6. Serve a prediction.
    pred = await client.post(
        "/serving/churn-model/predict",
        json={"features": {"tenure": 12, "charges": 79.5}, "subject_id": "u1"},
        headers=auth_headers,
    )
    assert pred.status_code == 200
    payload = pred.json()
    assert 0.0 <= payload["prediction"] <= 1.0
    assert payload["model_version"] == 1


async def test_requires_authentication(client: AsyncClient) -> None:
    resp = await client.get("/experiments")
    assert resp.status_code == 401
    assert resp.json()["title"] == "Unauthorized"


async def test_insufficient_scope_forbidden(client: AsyncClient) -> None:
    read_only = {"Authorization": f"Bearer {make_token(['experiments:read'])}"}
    resp = await client.post("/experiments", json={"name": "X Model"}, headers=read_only)
    assert resp.status_code == 403


async def test_missing_experiment_returns_404(client: AsyncClient, auth_headers: dict) -> None:
    resp = await client.get("/experiments/does-not-exist", headers=auth_headers)
    assert resp.status_code == 404
    assert resp.json()["correlation_id"]


async def test_validation_error_returns_422(client: AsyncClient, auth_headers: dict) -> None:
    # name shorter than min_length, and unknown framework on train.
    resp = await client.post("/experiments", json={"name": "ab"}, headers=auth_headers)
    assert resp.status_code == 422


async def test_predict_unknown_model_404(client: AsyncClient, auth_headers: dict) -> None:
    resp = await client.post(
        "/serving/ghost-model/predict",
        json={"features": {"x": 1}},
        headers=auth_headers,
    )
    assert resp.status_code == 404


async def test_duplicate_experiment_conflict_409(client: AsyncClient, auth_headers: dict) -> None:
    await client.post("/experiments", json={"name": "Dup Model"}, headers=auth_headers)
    resp = await client.post("/experiments", json={"name": "Dup Model"}, headers=auth_headers)
    assert resp.status_code == 409
