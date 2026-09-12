"""Async engine/session management.

Chooses the driver from the URL scheme:
  * ``sqlite://``    -> ``sqlite+aiosqlite://``   (dev / tests, no server needed)
  * ``postgresql://``-> ``postgresql+asyncpg://`` (staging / prod)
"""

from __future__ import annotations

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)

from trading_common.db.base import Base


def normalize_async_url(url: str) -> str:
    """Map a plain DB URL to its async driver variant."""
    if url.startswith("postgresql+asyncpg"):
        return url
    if url.startswith("postgresql"):
        return url.replace("postgresql", "postgresql+asyncpg", 1)
    if url.startswith("sqlite+aiosqlite"):
        return url
    if url.startswith("sqlite"):
        return url.replace("sqlite", "sqlite+aiosqlite", 1)
    return url


def make_engine(url: str, *, echo: bool = False) -> AsyncEngine:
    return create_async_engine(normalize_async_url(url), echo=echo, future=True)


def make_session_factory(engine: AsyncEngine) -> async_sessionmaker[AsyncSession]:
    return async_sessionmaker(engine, expire_on_commit=False, class_=AsyncSession)


async def create_all(engine: AsyncEngine) -> None:
    """Create tables from the ORM metadata (dev/test on SQLite).

    Production uses the reviewed SQL in ``db/migrations`` instead.
    """
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)


async def drop_all(engine: AsyncEngine) -> None:
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)


@asynccontextmanager
async def session_scope(
    factory: async_sessionmaker[AsyncSession],
) -> AsyncIterator[AsyncSession]:
    """Transactional scope: commit on success, rollback on error."""
    session = factory()
    try:
        yield session
        await session.commit()
    except Exception:
        await session.rollback()
        raise
    finally:
        await session.close()
