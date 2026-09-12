"""Test fixtures.

Integration tests need a real PostGIS instance (trigram + spatial SQL cannot run on
SQLite). They auto-skip when ``DATABASE_URL`` is unreachable, so the unit tests in
``test_health.py`` still run in any environment.

Point the suite at a database by exporting ``DATABASE_URL`` before running pytest, e.g.
``postgresql+asyncpg://geocoder:geocoder@localhost:5433/geocoder``.
"""

from __future__ import annotations

import asyncio
import os

# Must be set before importing the app (the engine binds to DATABASE_URL at import).
os.environ.setdefault(
    "DATABASE_URL", "postgresql+asyncpg://geocoder:geocoder@localhost:5433/geocoder"
)
os.environ.setdefault("ALLOWED_HOSTS", "*")
os.environ.setdefault("API_CORS_ORIGINS", "http://test")

import pytest  # noqa: E402
import pytest_asyncio  # noqa: E402
from httpx import ASGITransport, AsyncClient  # noqa: E402
from sqlalchemy import text  # noqa: E402
from sqlalchemy.ext.asyncio import create_async_engine  # noqa: E402

from app.api.deps import get_nominatim_client  # noqa: E402
from app.db.session import engine  # noqa: E402
from app.main import app  # noqa: E402
from app.services.nominatim import ProviderResult  # noqa: E402


def _detect_db() -> bool:
    async def _ping() -> None:
        probe = create_async_engine(os.environ["DATABASE_URL"])
        try:
            async with probe.connect() as connection:
                await connection.execute(text("SELECT 1"))
        finally:
            await probe.dispose()

    try:
        asyncio.run(_ping())
        return True
    except Exception:
        return False


DB_AVAILABLE = _detect_db()


class FakeNominatim:
    """Stand-in for the upstream client so integration tests stay offline and
    deterministic. Configure ``search_results`` / ``reverse_result`` per test."""

    def __init__(self) -> None:
        self.search_results: list[ProviderResult] = []
        self.reverse_result: ProviderResult | None = None
        self.search_calls = 0
        self.reverse_calls = 0

    async def search(self, query: str, limit: int) -> list[ProviderResult]:
        self.search_calls += 1
        return list(self.search_results[:limit])

    async def reverse(self, latitude: float, longitude: float) -> ProviderResult | None:
        self.reverse_calls += 1
        return self.reverse_result


@pytest.fixture
def fake_nominatim():
    fake = FakeNominatim()
    app.dependency_overrides[get_nominatim_client] = lambda: fake
    yield fake
    app.dependency_overrides.pop(get_nominatim_client, None)


@pytest_asyncio.fixture
async def _db():
    if not DB_AVAILABLE:
        pytest.skip("PostGIS not reachable on DATABASE_URL")
    async with engine.begin() as connection:
        await connection.execute(
            text("TRUNCATE addresses, address_lookup_events RESTART IDENTITY CASCADE")
        )
    yield
    # Dispose so the next test (running on a fresh event loop) does not reuse a
    # connection bound to this test's loop.
    await engine.dispose()


@pytest_asyncio.fixture
async def client(_db, fake_nominatim):
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as http_client:
        yield http_client
