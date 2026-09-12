"""Shared pytest fixtures available to every test in the repo.

Provides an isolated in-memory SQLite database per test (StaticPool keeps the
single in-memory connection alive across the async engine's pool), so the full
persistence layer is exercised with zero external services.
"""

from __future__ import annotations

import pytest_asyncio
from sqlalchemy.ext.asyncio import create_async_engine
from sqlalchemy.pool import StaticPool

from trading_common.db import create_all, make_session_factory


@pytest_asyncio.fixture
async def db_engine():
    engine = create_async_engine(
        "sqlite+aiosqlite://",
        poolclass=StaticPool,
        connect_args={"check_same_thread": False},
    )
    await create_all(engine)
    try:
        yield engine
    finally:
        await engine.dispose()


@pytest_asyncio.fixture
async def session_factory(db_engine):
    return make_session_factory(db_engine)
