"""Integration tests for search auth, envelope shape, and health probes."""

from __future__ import annotations

import pytest


@pytest.mark.asyncio
async def test_search_requires_api_key(anon_client) -> None:
    resp = await anon_client.post("/api/v1/search", json={"query": "hello", "k": 5})
    assert resp.status_code == 401
    assert resp.json()["error"]["code"] == "unauthorized"


@pytest.mark.asyncio
async def test_search_empty_index_returns_empty(client) -> None:
    resp = await client.post("/api/v1/search", json={"query": "anything", "k": 5, "mode": "vector"})
    assert resp.status_code == 200
    body = resp.json()
    assert body["query"] == "anything"
    assert body["backend"] == "memory"
    assert body["results"] == []
    assert body["took_ms"] is not None


@pytest.mark.asyncio
async def test_liveness_probe(client) -> None:
    resp = await client.get("/api/v1/health/live")
    assert resp.status_code == 200
    assert resp.json()["status"] == "ok"


@pytest.mark.asyncio
async def test_readiness_probe(client) -> None:
    resp = await client.get("/api/v1/health/ready")
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "ok"
    assert body["checks"]["database"] is True
    assert body["checks"]["vector_backend"] is True
