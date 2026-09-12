from __future__ import annotations


def test_healthz(client):
    resp = client.get("/healthz")
    assert resp.status_code == 200
    assert resp.json()["status"] == "ok"


def test_readyz_reports_db_ok(client):
    resp = client.get("/readyz")
    assert resp.status_code == 200
    body = resp.json()
    assert body["database"] == "ok"
    assert body["status"] == "ready"
    # Redis is disabled in tests -> reported unavailable, but readiness is DB-gated.
    assert body["redis"] in {"ok", "unavailable"}


def test_request_id_header_present(client):
    resp = client.get("/healthz")
    assert "x-request-id" in resp.headers
