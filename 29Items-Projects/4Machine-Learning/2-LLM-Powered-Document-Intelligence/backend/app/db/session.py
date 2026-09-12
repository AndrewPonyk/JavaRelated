"""Async SQLAlchemy engine + session factory.

The engine is created lazily on first use so importing the app never eagerly loads the
production driver (asyncpg) — tests run against SQLite and override ``get_session``
entirely. One session per request; never share a session across tasks (TECH-NOTES §3.6).
"""

from __future__ import annotations

from collections.abc import AsyncIterator
from functools import lru_cache

from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)

from app.core.config import settings


@lru_cache
def get_engine() -> AsyncEngine:
    return create_async_engine(settings.database_url, pool_pre_ping=True, echo=False)


@lru_cache
def get_sessionmaker() -> async_sessionmaker[AsyncSession]:
    return async_sessionmaker(bind=get_engine(), expire_on_commit=False, autoflush=False)


async def get_session() -> AsyncIterator[AsyncSession]:
    """FastAPI dependency yielding a request-scoped async session."""
    async with get_sessionmaker()() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise


# Convenience alias used by the worker (opens its own sessions).
def SessionLocal() -> AsyncSession:  # noqa: N802 — keeps the familiar call site
    return get_sessionmaker()()
