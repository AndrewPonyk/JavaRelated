"""Database engine and session factory.

SQLAlchemy 2.0 style. Works with Postgres (production) and SQLite (tests/local). The
pool stays small because Cloud Run scales horizontally — many instances, each with a
modest pool, must not exhaust Cloud SQL connection limits.
"""

from __future__ import annotations

from collections.abc import Iterator

from sqlalchemy import Engine, create_engine
from sqlalchemy.orm import Session, sessionmaker

from app.core.config import get_settings
from app.models.db_models import Base


def _make_engine(url: str) -> Engine:
    if url.startswith("sqlite"):
        # check_same_thread=False so the TestClient's threads can share the DB.
        # StaticPool keeps a single connection so an in-memory DB is shared across
        # sessions/threads (essential for tests).
        from sqlalchemy.pool import StaticPool

        connect_args = {"check_same_thread": False}
        if ":memory:" in url or url == "sqlite://":
            return create_engine(url, connect_args=connect_args, poolclass=StaticPool, future=True)
        return create_engine(url, connect_args=connect_args, future=True)
    return create_engine(url, pool_size=5, max_overflow=5, pool_pre_ping=True, future=True)


engine = _make_engine(get_settings().database_url)
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)


def init_db() -> None:
    """Create tables if they don't exist (used for SQLite/local; prod uses Alembic)."""
    Base.metadata.create_all(bind=engine)


def get_db() -> Iterator[Session]:
    """FastAPI dependency that yields a scoped DB session."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
