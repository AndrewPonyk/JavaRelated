"""Engine + session management. The only module that connects to the database."""

from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager

from sqlalchemy import Engine, create_engine
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.core.errors import StorageError

_engine: Engine | None = None


def get_engine() -> Engine:
    """Process-wide engine singleton (Streamlit reruns must not leak pools)."""
    global _engine
    if _engine is None:
        settings = get_settings()
        if settings.database_url is None:
            raise StorageError(
                "DATABASE_URL is not configured",
                user_message=(
                    "No database is configured — running in demo mode; results are not persisted."
                ),
            )
        url = settings.database_url
        if url.startswith("sqlite"):
            # SQLite (local dev / tests) uses its own pooling; QueuePool args don't apply.
            _engine = create_engine(url)
        else:
            # Small pool on purpose: managed-Postgres connection slots are the scarce
            # resource, not CPU (docs/TECH-NOTES.md §3.6.7).
            _engine = create_engine(
                url,
                pool_size=5,
                max_overflow=5,
                pool_pre_ping=True,
                pool_recycle=1800,
            )
    return _engine


def dispose_engine() -> None:
    """Close the pool and forget the singleton (tests, scripts, graceful shutdown)."""
    global _engine
    if _engine is not None:
        _engine.dispose()
        _engine = None


@contextmanager
def session_scope() -> Iterator[Session]:
    """Transactional scope: commit on success, rollback on error, always close."""
    session = Session(get_engine())
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()
