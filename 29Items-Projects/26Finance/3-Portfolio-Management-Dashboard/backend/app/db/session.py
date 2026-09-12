"""Database engine and session factory.

Exposes ``get_db`` — the FastAPI dependency that yields a request-scoped session
and guarantees it is closed afterwards. The engine adapts to the backend: full
connection pooling for PostgreSQL, SQLite-friendly args for the test database.
"""

from __future__ import annotations

from collections.abc import Generator

from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import StaticPool

from app.core.config import get_settings

settings = get_settings()

_url = settings.sqlalchemy_database_uri
_engine_kwargs: dict = {"future": True, "pool_pre_ping": True}
_connect_args: dict = {}

if _url.startswith("sqlite"):
    # Allow cross-thread use (TestClient) and share one in-memory DB.
    _connect_args = {"check_same_thread": False}
    _engine_kwargs.pop("pool_pre_ping", None)
    if ":memory:" in _url:
        _engine_kwargs["poolclass"] = StaticPool
else:
    _engine_kwargs.update(pool_size=10, max_overflow=20)

engine = create_engine(_url, connect_args=_connect_args, **_engine_kwargs)

SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False, future=True)


def get_db() -> Generator[Session, None, None]:
    """Yield a DB session, ensuring it is always closed."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
