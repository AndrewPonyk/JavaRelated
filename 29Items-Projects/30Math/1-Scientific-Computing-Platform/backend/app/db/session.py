"""Async SQLAlchemy engine/session wiring.

Everything is lazy (created on first use, cached) so importing the app never
requires a reachable database — tests and tooling import freely.
"""

from __future__ import annotations

from collections.abc import AsyncIterator
from functools import lru_cache

from sqlalchemy.ext.asyncio import (
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)

from app.core.config import get_settings


@lru_cache
def get_engine():
    url = get_settings().database_url
    if url.startswith("sqlite"):
        # Test/local-file mode: a generous lock timeout because the sync
        # worker engine writes to the same file.
        return create_async_engine(url, connect_args={"timeout": 30})
    return create_async_engine(
        url,
        pool_pre_ping=True,
        pool_size=5,
        max_overflow=10,
    )


@lru_cache
def get_sessionmaker() -> async_sessionmaker[AsyncSession]:
    # expire_on_commit=False: services return ORM rows after commit without
    # surprise lazy-load IO (docs/TECH-NOTES.md §3.6, MissingGreenlet foot-gun).
    return async_sessionmaker(get_engine(), expire_on_commit=False)


async def get_db_session() -> AsyncIterator[AsyncSession]:
    async with get_sessionmaker()() as session:
        yield session
