from __future__ import annotations

import contextlib
import os
import tempfile
import uuid
from pathlib import Path

# Configure the environment BEFORE importing the app so Settings pick it up:
# a throwaway SQLite DB, inline (eager) jobs, and cache/rate-limit disabled.
_TEST_DB = Path(tempfile.gettempdir()) / f"pmd_test_{uuid.uuid4().hex[:8]}.db"
os.environ["DATABASE_URL"] = f"sqlite:///{_TEST_DB.as_posix()}"
os.environ["CELERY_TASK_ALWAYS_EAGER"] = "true"
os.environ["CACHE_ENABLED"] = "false"
os.environ["RATE_LIMIT_ENABLED"] = "false"
os.environ["SECRET_KEY"] = "test-secret-key-which-is-long-enough-1234567890"
os.environ["APP_ENV"] = "test"

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

import app.models  # noqa: E402,F401 - register tables on Base.metadata
from app.analytics import estimate_mu_sigma  # noqa: E402
from app.db.base import Base  # noqa: E402
from app.db.session import engine  # noqa: E402
from app.main import app  # noqa: E402
from app.services.market_data_service import generate_synthetic_prices  # noqa: E402


@pytest.fixture(scope="session", autouse=True)
def _schema():
    Base.metadata.create_all(engine)
    yield
    Base.metadata.drop_all(engine)
    with contextlib.suppress(OSError):
        _TEST_DB.unlink(missing_ok=True)


# --------------------------------------------------------------------------- #
# Analytics fixtures (pure-core tests)
# --------------------------------------------------------------------------- #
@pytest.fixture
def symbols() -> list[str]:
    return ["AAA", "BBB", "CCC", "DDD"]


@pytest.fixture
def prices(symbols):
    return generate_synthetic_prices(symbols, days=504, seed=7)


@pytest.fixture
def mu_cov(prices):
    return estimate_mu_sigma(prices)


# --------------------------------------------------------------------------- #
# API fixtures (integration tests)
# --------------------------------------------------------------------------- #
@pytest.fixture
def client():
    return TestClient(app)


def _register_and_login(client: TestClient) -> dict[str, str]:
    email = f"user_{uuid.uuid4().hex[:8]}@example.com"
    reg = client.post(
        "/api/v1/auth/register",
        json={"email": email, "password": "password123", "full_name": "Test User"},
    )
    assert reg.status_code == 201, reg.text
    login = client.post("/api/v1/auth/login", data={"username": email, "password": "password123"})
    assert login.status_code == 200, login.text
    return {"Authorization": f"Bearer {login.json()['access_token']}"}


@pytest.fixture
def auth_headers(client) -> dict[str, str]:
    return _register_and_login(client)


@pytest.fixture
def other_headers(client) -> dict[str, str]:
    """A second, distinct authenticated user (for ownership tests)."""
    return _register_and_login(client)


@pytest.fixture
def seeded_assets(client, auth_headers) -> list[int]:
    asset_ids = []
    for _ in range(3):
        symbol = f"T{uuid.uuid4().hex[:6].upper()}"
        created = client.post(
            "/api/v1/assets",
            json={"symbol": symbol, "name": f"{symbol} Corp"},
            headers=auth_headers,
        )
        assert created.status_code == 201, created.text
        aid = created.json()["id"]
        seeded = client.post(f"/api/v1/assets/{aid}/seed-prices?days=504", headers=auth_headers)
        assert seeded.status_code == 201, seeded.text
        asset_ids.append(aid)
    return asset_ids


@pytest.fixture
def portfolio(client, auth_headers, seeded_assets) -> dict:
    holdings = [{"asset_id": aid, "quantity": 10, "cost_basis": 100} for aid in seeded_assets]
    created = client.post(
        "/api/v1/portfolios",
        json={"name": "Test Portfolio", "holdings": holdings},
        headers=auth_headers,
    )
    assert created.status_code == 201, created.text
    return created.json()
