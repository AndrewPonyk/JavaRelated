"""Shared backend test fixtures.

Strategy (docs/TECH-NOTES.md §3.2): tests drive the REAL ASGI app in-process
against a per-test SQLite file (the models are backend-portable); CI runs the
same suite against the Postgres service container by exporting
TEST_DATABASE_URL. Celery executes eagerly (inline), so the full
queue→worker→persist flow is exercised without a broker.
"""

from __future__ import annotations

import os
import uuid

import pytest
from fastapi.testclient import TestClient


def _clear_runtime_caches() -> None:
    """Reset every lru_cache that captures settings-derived state."""
    from app.core.config import get_settings
    from app.core.ratelimit import get_rate_limiter
    from app.db import session as async_session_module
    from app.db import sync_session as sync_session_module
    from app.services import artifact_store, cache

    get_settings.cache_clear()
    async_session_module.get_engine.cache_clear()
    async_session_module.get_sessionmaker.cache_clear()
    sync_session_module.get_sync_engine.cache_clear()
    sync_session_module.get_sync_sessionmaker.cache_clear()
    cache.get_cache.cache_clear()
    artifact_store.get_artifact_store.cache_clear()
    get_rate_limiter.cache_clear()


@pytest.fixture()
def app_env(tmp_path, monkeypatch):
    """Isolated app environment: fresh DB file, tmp artifacts, eager celery."""
    test_db_url = os.environ.get("TEST_DATABASE_URL")
    if test_db_url:
        database_url = test_db_url  # CI: postgres service container
    else:
        database_url = f"sqlite+aiosqlite:///{(tmp_path / 'test.db').as_posix()}"

    monkeypatch.setenv("ENVIRONMENT", "test")
    monkeypatch.setenv("DATABASE_URL", database_url)
    monkeypatch.setenv("CELERY_TASK_ALWAYS_EAGER", "true")
    monkeypatch.setenv("ARTIFACTS_DIR", str(tmp_path / "artifacts"))
    monkeypatch.setenv("S3_ARTIFACTS_BUCKET", "")
    monkeypatch.setenv("SAGEMAKER_RECOGNIZER_ENDPOINT", "")
    monkeypatch.setenv("JWT_SECRET_KEY", "test-secret-not-for-production-0123456789abcdef")
    # Rate limiting is off by default in tests (fixtures fire many requests);
    # the dedicated rate-limit tests re-enable it with a small limit.
    monkeypatch.setenv("RATE_LIMIT_PER_MINUTE", "0")
    # Redis is intentionally unreachable in local tests: the cache must fall
    # back to memory and readyz must report degraded.
    monkeypatch.setenv("REDIS_URL", os.environ.get("TEST_REDIS_URL", "redis://127.0.0.1:1/9"))

    _clear_runtime_caches()

    from app.db.models import Base
    from app.db.sync_session import get_sync_engine

    engine = get_sync_engine()
    Base.metadata.drop_all(engine)
    Base.metadata.create_all(engine)

    yield

    _clear_runtime_caches()


@pytest.fixture()
def client(app_env) -> TestClient:
    from app.main import create_app

    # raise_server_exceptions=False: let OUR problem+json handlers answer,
    # exactly as they would in production.
    return TestClient(create_app(), raise_server_exceptions=False)


@pytest.fixture()
def user_factory(client):
    """Register+login helper. Returns (headers, tokens) for a fresh user."""

    def make_user(password: str = "correct-horse-battery"):
        email = f"student-{uuid.uuid4().hex[:10]}@example.edu"
        register = client.post("/api/v1/auth/register", json={"email": email, "password": password})
        assert register.status_code == 201, register.text
        login = client.post("/api/v1/auth/login", json={"email": email, "password": password})
        assert login.status_code == 200, login.text
        tokens = login.json()
        headers = {"Authorization": f"Bearer {tokens['access_token']}"}
        return {"email": email, "headers": headers, "tokens": tokens}

    return make_user


@pytest.fixture()
def auth_headers(user_factory) -> dict[str, str]:
    return user_factory()["headers"]
