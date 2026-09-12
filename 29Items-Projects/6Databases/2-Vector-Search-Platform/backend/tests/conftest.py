"""Shared test configuration + fixtures.

Runs the whole stack **offline and deterministically**: hashing embedder (no model download),
the in-memory vector backend, and a temp-file SQLite database. Env is set *before* importing any
app module so cached Settings + the DB engine pick it up.
"""

from __future__ import annotations

import os
import pathlib
import tempfile

# ── Configure the environment BEFORE importing app code ──────────────────────
_TMPDIR = tempfile.mkdtemp(prefix="vsp_test_")
_DB_PATH = pathlib.Path(_TMPDIR) / "test.db"
os.environ.update(
    {
        "APP_ENV": "development",
        "EMBEDDING_PROVIDER": "hashing",
        "DEFAULT_BACKEND": "memory",
        "DATABASE_URL": f"sqlite+aiosqlite:///{_DB_PATH.as_posix()}",
        "REDIS_URL": "",  # empty => cache disabled
        "API_KEY": "test-secret-key",
        "ENABLE_RERANKER": "false",
    }
)

API_KEY = os.environ["API_KEY"]

import pytest_asyncio  # noqa: E402
from httpx import ASGITransport, AsyncClient  # noqa: E402


@pytest_asyncio.fixture(autouse=True)
async def _db_and_stores():
    """Fresh schema + fresh in-memory backend for every test."""
    from app.db.session import create_all, drop_all
    from app.vectorstores import reset_stores

    await create_all()
    reset_stores()
    try:
        yield
    finally:
        await drop_all()
        reset_stores()


@pytest_asyncio.fixture
async def client():
    """Authenticated async HTTP client bound to the ASGI app."""
    from app.main import app

    transport = ASGITransport(app=app)
    async with AsyncClient(
        transport=transport, base_url="http://test", headers={"X-API-Key": API_KEY}
    ) as http_client:
        yield http_client


@pytest_asyncio.fixture
async def anon_client():
    """Unauthenticated client (no API key) for auth tests."""
    from app.main import app

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as http_client:
        yield http_client
