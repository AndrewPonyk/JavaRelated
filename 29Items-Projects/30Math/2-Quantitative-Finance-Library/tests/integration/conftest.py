"""Integration fixtures: real FastAPI app on a throwaway SQLite database.

`make_client` builds an app with per-test env overrides; `client` is the
frictionless dev-mode app (no keys), `keyed_client` has one key per scope.
"""

from __future__ import annotations

import hashlib

import pytest
from fastapi.testclient import TestClient

ALL_KEY = "test-all-scopes-key"
PRICE_KEY = "test-price-only-key"
FIT_KEY = "test-fit-only-key"
ADMIN_KEY = "test-admin-only-key"


def key_hash(key: str) -> str:
    return hashlib.sha256(key.encode()).hexdigest()


KEYED_ENV = ",".join(
    [
        key_hash(ALL_KEY),
        f"{key_hash(PRICE_KEY)}:price",
        f"{key_hash(FIT_KEY)}:fit",
        f"{key_hash(ADMIN_KEY)}:admin",
    ]
)


@pytest.fixture()
def make_client(tmp_path, monkeypatch):
    """Factory for TestClients with per-test env overrides. Callers must use
    the returned client as a context manager (lifespan start/stop)."""

    def factory(**env: str) -> TestClient:
        defaults = {
            "QF_ENV": "dev",
            "QF_DATABASE_URL": f"sqlite+aiosqlite:///{tmp_path}/test.db",
            "QF_API_KEY_HASHES": "",
            "QF_RATE_LIMIT": "100000/minute",  # rate-limit tests override this
        }
        defaults.update(env)
        for name, value in defaults.items():
            monkeypatch.setenv(name, value)

        # Settings are cached per-process and the limiter is module state;
        # reset both so each test gets a clean app.
        from app.config import get_settings
        from app.dependencies import rate_limiter

        get_settings.cache_clear()
        rate_limiter.reset()

        from app.main import create_app

        return TestClient(create_app())

    yield factory

    from app.config import get_settings

    get_settings.cache_clear()


@pytest.fixture()
def client(make_client):
    """Dev-mode app: no keys configured -> anonymous access, all scopes."""
    with make_client() as test_client:
        yield test_client


@pytest.fixture()
def keyed_client(make_client):
    """App with four keys: one all-scopes, one per single scope."""
    with make_client(QF_API_KEY_HASHES=KEYED_ENV) as test_client:
        yield test_client
