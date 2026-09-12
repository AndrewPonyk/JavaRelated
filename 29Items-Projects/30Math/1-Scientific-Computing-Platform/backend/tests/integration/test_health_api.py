"""Probe endpoints: liveness is dependency-free; readiness tells the truth."""

from fastapi.testclient import TestClient


def test_healthz_is_always_ok(client):
    response = client.get("/healthz")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_readyz_reports_degraded_when_redis_is_down(app_env, monkeypatch):
    # Force an unreachable broker regardless of the environment (CI runs a
    # real Redis service; locally there may be none — both must behave).
    monkeypatch.setenv("REDIS_URL", "redis://127.0.0.1:1/9")
    from tests.conftest import _clear_runtime_caches

    _clear_runtime_caches()
    from app.main import create_app

    client = TestClient(create_app(), raise_server_exceptions=False)
    response = client.get("/readyz")
    assert response.status_code == 503
    body = response.json()
    assert body["status"] == "degraded"
    assert body["database"] == "ok"  # the DB engine answers SELECT 1
    assert body["redis"].startswith("error:")


def test_readyz_ok_when_all_dependencies_answer(client, monkeypatch):
    async def fake_redis_ok() -> str:
        return "ok"

    import app.api.v1.endpoints.health as health

    monkeypatch.setattr(health, "_check_redis", fake_redis_ok)
    response = client.get("/readyz")
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "database": "ok", "redis": "ok"}
