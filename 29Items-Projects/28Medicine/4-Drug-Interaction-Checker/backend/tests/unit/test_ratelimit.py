"""Tests for the rate-limit middleware."""

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.core.ratelimit import RateLimitMiddleware


def _make_app(limit: int) -> FastAPI:
    app = FastAPI()
    app.add_middleware(RateLimitMiddleware, limit_per_minute=limit)

    @app.get("/x")
    def x():
        return {"ok": True}

    @app.get("/api/v1/health/live")
    def health():
        return {"ok": True}

    return app


def test_allows_under_limit():
    client = TestClient(_make_app(5))
    assert client.get("/x").status_code == 200


def test_blocks_over_limit():
    client = TestClient(_make_app(2))
    client.get("/x")
    client.get("/x")
    resp = client.get("/x")
    assert resp.status_code == 429
    assert resp.json()["error"]["code"] == "rate_limited"
    assert "Retry-After" in resp.headers


def test_disabled_when_zero():
    client = TestClient(_make_app(0))
    for _ in range(10):
        assert client.get("/x").status_code == 200


def test_health_path_exempt():
    client = TestClient(_make_app(1))
    client.get("/api/v1/health/live")
    assert client.get("/api/v1/health/live").status_code == 200
