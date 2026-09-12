"""Async SQLAlchemy engine + session factory.

Builds an engine from ``DATABASE_URL``. For in-memory SQLite (tests) it uses a StaticPool so
every connection shares the same database. Exposes ``get_session`` (FastAPI dependency) plus
``create_all`` / ``drop_all`` helpers for dev + tests (production uses Alembic migrations).
"""

from __future__ import annotations

from collections.abc import AsyncIterator
from typing import Any

from sqlalchemy import event
from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)
from sqlalchemy.pool import NullPool, StaticPool

from app.core.config import get_settings
from app.db.base import Base


def _build_engine() -> AsyncEngine:
    settings = get_settings()
    url = settings.database_url
    kwargs: dict[str, Any] = {"future": True, "pool_pre_ping": True}

    if url.startswith("sqlite"):
        kwargs.pop("pool_pre_ping", None)
        kwargs["connect_args"] = {"check_same_thread": False}
        if ":memory:" in url or url.endswith("sqlite+aiosqlite://"):
            # in-memory sqlite needs a single shared connection across the app.
            kwargs["poolclass"] = StaticPool
        else:
            # file-based sqlite (tests): NullPool avoids reusing a connection across
            # different event loops (each test gets its own loop).
            kwargs["poolclass"] = NullPool

    async_engine = create_async_engine(url, **kwargs)

    if url.startswith("sqlite"):
        # SQLite ignores foreign keys (incl. ON DELETE CASCADE) unless enabled per-connection.
        @event.listens_for(async_engine.sync_engine, "connect")
        def _enable_sqlite_fks(dbapi_conn: Any, _record: Any) -> None:
            cursor = dbapi_conn.cursor()
            cursor.execute("PRAGMA foreign_keys=ON")
            cursor.close()

    return async_engine


engine: AsyncEngine = _build_engine()
SessionLocal = async_sessionmaker(engine, expire_on_commit=False, class_=AsyncSession)


async def get_session() -> AsyncIterator[AsyncSession]:
    """Yield a transactional session; commit on success, roll back on error."""
    async with SessionLocal() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise


async def create_all() -> None:
    """Create ORM tables (dev/sqlite/tests). Production uses Alembic."""
    import app.models  # noqa: F401 - ensure models are registered on Base.metadata

    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)


async def drop_all() -> None:
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)
