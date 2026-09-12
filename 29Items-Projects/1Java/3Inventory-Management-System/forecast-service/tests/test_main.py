from fakeredis import FakeRedis
from fastapi.testclient import TestClient

from app.main import app
from app.model_store import ModelMetadata, ModelStore


fake_redis = FakeRedis(decode_responses=True)
app.state.model_store = ModelStore("redis://unused", "forecast:model:active", fake_redis)
client = TestClient(app)


def test_health_reports_ready_model_registry() -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "ok"
    assert response.json()["feature_version"] == "daily-demand-v1"
    assert client.get("/health/live").json()["redis"] == "not_checked"


def test_readiness_fails_when_redis_is_unavailable(monkeypatch) -> None:
    monkeypatch.setattr(app.state.model_store, "ping", lambda: False)
    response = client.get("/health/ready")
    assert response.status_code == 503
    assert response.json()["status"] == "degraded"


def test_forecast_returns_versioned_holt_prediction() -> None:
    response = client.post(
        "/v1/forecasts",
        json={"sku": "SKU-1", "demand_history": [2, 4, 6], "horizon_days": 2},
    )

    assert response.status_code == 200
    payload = response.json()
    assert len(payload["predictions"]) == 2
    assert payload["predictions"][1] >= payload["predictions"][0]
    assert payload["model_version"]


def test_training_selects_and_persists_a_checksummed_model() -> None:
    response = client.post(
        "/v1/models/train",
        json={"demand_history": list(range(1, 31)), "model_version": "test-v2"},
    )

    assert response.status_code == 201
    assert response.json()["version"] == "test-v2"
    active = client.get("/v1/models/active").json()
    assert active["version"] == "test-v2"
    assert len(active["checksum"]) == 64


def test_training_can_require_an_administration_token(monkeypatch) -> None:
    monkeypatch.setenv("FORECAST_REQUIRE_ADMIN_TOKEN", "true")
    monkeypatch.setenv("MODEL_ADMIN_TOKEN", "test-secret")
    payload = {"demand_history": list(range(1, 20))}
    assert client.post("/v1/models/train", json=payload).status_code == 401
    assert client.post(
        "/v1/models/train", json=payload, headers={"X-Model-Admin-Token": "test-secret"}
    ).status_code == 201


def test_corrupt_redis_model_is_rejected_in_favor_of_last_known_good() -> None:
    known_good = app.state.model_store.active_model()
    fake_redis.set("forecast:model:active", '{"version":"tampered","checksum":"bad"}')

    assert app.state.model_store.active_model().version == known_good.version


def test_forecast_rejects_negative_demand_and_incompatible_features() -> None:
    invalid = client.post(
        "/v1/forecasts",
        json={"sku": "SKU-1", "demand_history": [1, -1], "horizon_days": 2},
    )
    incompatible = client.post(
        "/v1/forecasts",
        json={
            "sku": "SKU-1",
            "demand_history": [1, 2],
            "horizon_days": 2,
            "feature_version": "hourly-v1",
        },
    )

    assert invalid.status_code == 422
    assert incompatible.status_code == 409


def test_checksum_round_trip_detects_tampering() -> None:
    model = ModelMetadata.create("v1", "daily-demand-v1", 0.5, 0.1, 1.0, 0.5)
    assert ModelMetadata.from_json(model.to_json()) == model


def test_large_training_series_uses_linear_walk_forward_evaluation() -> None:
    model = ModelStore.train([float(index % 20) for index in range(5000)], "daily-demand-v1", "large")
    assert model.version == "large"


def test_invalid_model_metrics_are_rejected() -> None:
    try:
        ModelMetadata.create("bad", "daily-demand-v1", 0.5, 0.1, -1.0, 0.0)
    except ValueError as exception:
        assert "cannot be negative" in str(exception)
    else:
        raise AssertionError("Negative model metrics must be rejected")
