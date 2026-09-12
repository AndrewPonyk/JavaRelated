"""Async SQLAlchemy engine + session factory.

Engine tuning is dialect-aware: PostgreSQL (production) gets connection pooling;
SQLite (tests/dev) uses a StaticPool so an in-memory DB survives across the
connections of a single engine.
"""

from __future__ import annotations

from collections.abc import AsyncIterator
from typing import Any

from sqlalchemy.ext.asyncio import (
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)
from sqlalchemy.pool import StaticPool

from app.core.config import settings


def _engine_kwargs(url: str) -> dict[str, Any]:
    if url.startswith("sqlite"):
        return {
            "connect_args": {"check_same_thread": False},
            "poolclass": StaticPool,
        }
    return {
        "pool_pre_ping": True,  # survive RDS failovers / idle disconnects
        "pool_size": 10,
        "max_overflow": 20,
    }


engine = create_async_engine(
    settings.database_url, echo=False, **_engine_kwargs(settings.database_url)
)

SessionFactory = async_sessionmaker(engine, expire_on_commit=False, class_=AsyncSession)


async def get_session() -> AsyncIterator[AsyncSession]:
    """FastAPI dependency: one session per request, always closed."""
    async with SessionFactory() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
