from __future__ import annotations

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.core.rate_limit import RateLimitMiddleware


def _app(limit: int) -> FastAPI:
    app = FastAPI()
    app.add_middleware(RateLimitMiddleware, limit_per_minute=limit)

    @app.get("/ping")
    def ping():
        return {"ok": True}

    @app.get("/healthz")
    def healthz():
        return {"status": "ok"}

    return app


def test_blocks_after_limit():
    client = TestClient(_app(limit=3))
    for _ in range(3):
        assert client.get("/ping").status_code == 200
    blocked = client.get("/ping")
    assert blocked.status_code == 429
    assert blocked.json()["error"]["code"] == "rate_limited"
    assert "Retry-After" in blocked.headers


def test_health_paths_are_exempt():
    client = TestClient(_app(limit=1))
    for _ in range(5):
        assert client.get("/healthz").status_code == 200
