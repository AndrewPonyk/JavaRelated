"""Liveness and readiness semantics."""

from __future__ import annotations

import httpx

from app.db.session import get_db


def test_healthz(client):
    response = client.get("/healthz")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_readyz_ok_when_db_up_and_trino_down(client, monkeypatch):
    def refuse(*args, **kwargs):
        raise httpx.ConnectError("connection refused")

    monkeypatch.setattr(httpx, "get", refuse)
    response = client.get("/readyz")
    assert response.status_code == 200
    body = response.json()
    assert body["checks"]["database"] == "ok"
    assert body["checks"]["trino"] == "unreachable"


def test_readyz_reports_trino_ok(client, monkeypatch):
    monkeypatch.setattr(
        httpx, "get", lambda *a, **k: httpx.Response(200, request=httpx.Request("GET", "http://t"))
    )
    assert client.get("/readyz").json()["checks"]["trino"] == "ok"


def test_readyz_503_when_database_down(make_client, monkeypatch):
    class BrokenSession:
        def execute(self, *args, **kwargs):
            raise RuntimeError("db is down")

        def close(self):
            return None

    def broken_db():
        yield BrokenSession()

    client = make_client({get_db: broken_db})
    monkeypatch.setattr(
        httpx, "get", lambda *a, **k: (_ for _ in ()).throw(httpx.ConnectError("down"))
    )

    response = client.get("/readyz")
    assert response.status_code == 503
    assert response.json()["checks"]["database"] == "unavailable"
