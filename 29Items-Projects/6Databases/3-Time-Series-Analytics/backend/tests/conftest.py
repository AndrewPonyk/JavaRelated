"""Shared fixtures.

`client` (and its alias `offline_client`) runs the app with every backend
deterministically ABSENT — degraded mode. API tests patch the repository or
service layer, so no real database is ever touched and runs are fast and
environment-independent. The `requires_stack` suite builds its own client
against live Cassandra/Redis (tests/integration/test_stack_roundtrip.py).
"""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from app.core.security import create_access_token
from app.db import BackendUnavailableError


def _make_offline_client(monkeypatch):
    async def refuse() -> None:
        raise BackendUnavailableError("forced offline (test fixture)")

    for module in ("cassandra", "redis", "influx"):
        monkeypatch.setattr(f"app.db.{module}.connect", refuse)
    monkeypatch.setattr("app.db.cassandra._session", None)
    monkeypatch.setattr("app.db.cassandra._cluster", None)
    monkeypatch.setattr("app.db.redis._client", None)
    monkeypatch.setattr("app.db.influx._client", None)
    monkeypatch.setattr("app.db.influx._write_api", None)

    from app.main import create_app

    return TestClient(create_app())


@pytest.fixture()
def client(monkeypatch):
    with _make_offline_client(monkeypatch) as test_client:
        yield test_client


@pytest.fixture()
def offline_client(monkeypatch):
    """Alias of `client` — used where the test's POINT is degraded mode."""
    with _make_offline_client(monkeypatch) as test_client:
        yield test_client


@pytest.fixture()
def operator_headers() -> dict[str, str]:
    token = create_access_token("op@test", ["operator"])
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture()
def viewer_headers() -> dict[str, str]:
    token = create_access_token("viewer@test", ["viewer"])
    return {"Authorization": f"Bearer {token}"}
