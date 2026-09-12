"""Async SQLAlchemy engine and session factory for platform metadata.

Works on SQLite (dev/test) and PostgreSQL (prod) — the driver is selected by
``DATABASE_URL`` (``sqlite+aiosqlite://...`` or ``postgresql+asyncpg://...``).
"""
from __future__ import annotations

from collections.abc import AsyncIterator

from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)

from src.core.config import get_settings
from src.db.models import Base

_engine: AsyncEngine | None = None
_session_factory: async_sessionmaker[AsyncSession] | None = None


def get_engine() -> AsyncEngine:
    """Lazily construct the process-wide async engine."""
    global _engine
    if _engine is None:
        settings = get_settings()
        is_sqlite = settings.database_url.startswith("sqlite")
        _engine = create_async_engine(
            settings.database_url,
            echo=False,
            # pool_pre_ping is meaningful for server DBs, not file SQLite.
            pool_pre_ping=not is_sqlite,
        )
    return _engine


def get_session_factory() -> async_sessionmaker[AsyncSession]:
    global _session_factory
    if _session_factory is None:
        _session_factory = async_sessionmaker(bind=get_engine(), expire_on_commit=False)
    return _session_factory


async def init_models() -> None:
    """Create tables if missing (idempotent).

    Used for SQLite dev/test and first-run convenience. In production, schema is
    owned by Alembic migrations; ``create_all`` only adds missing tables and so
    is safe to call alongside them.
    """
    async with get_engine().begin() as conn:
        await conn.run_sync(Base.metadata.create_all)


async def dispose_engine() -> None:
    """Dispose the engine's connection pool on shutdown."""
    global _engine, _session_factory
    if _engine is not None:
        await _engine.dispose()
        _engine = None
        _session_factory = None


async def get_session() -> AsyncIterator[AsyncSession]:
    """Yield a request-scoped session; commit on success, rollback on error."""
    factory = get_session_factory()
    async with factory() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
