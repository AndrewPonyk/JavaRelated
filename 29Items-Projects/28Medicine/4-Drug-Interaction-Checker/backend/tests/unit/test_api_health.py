"""API tests for health/liveness/readiness endpoints."""

import app.api.v1.endpoints.health as health_module


def test_health_ok(client, monkeypatch):
    async def connected():
        return True

    monkeypatch.setattr(health_module, "verify_connectivity", connected)
    resp = client.get("/api/v1/health")
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "ok"
    assert body["neo4j_connected"] is True


def test_health_degraded(client, monkeypatch):
    async def disconnected():
        return False

    monkeypatch.setattr(health_module, "verify_connectivity", disconnected)
    body = client.get("/api/v1/health").json()
    assert body["status"] == "degraded"
    assert body["neo4j_connected"] is False


def test_liveness(client):
    assert client.get("/api/v1/health/live").json()["status"] == "ok"


def test_readiness_not_ready(client, monkeypatch):
    async def disconnected():
        return False

    monkeypatch.setattr(health_module, "verify_connectivity", disconnected)
    assert client.get("/api/v1/health/ready").json()["status"] == "not_ready"
