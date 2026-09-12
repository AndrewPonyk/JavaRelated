"""Health/readiness probe tests."""

from __future__ import annotations

PREFIX = "/api/v1"


async def test_live(client):
    resp = await client.get(f"{PREFIX}/health/live")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok"}


async def test_ready_reports_dependencies(client):
    resp = await client.get(f"{PREFIX}/health/ready")
    assert resp.status_code == 200
    body = resp.json()
    assert body["database"] == "ok"
    assert body["storage"] == "ok"
    assert body["status"] == "ready"
