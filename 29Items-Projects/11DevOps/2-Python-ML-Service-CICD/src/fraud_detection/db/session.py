"""Lazy SQLAlchemy engine and session factory.

Nothing here touches the database at import time - the engine is created on
first use so the app (and the test suite) can start without a database
server. SQLite (the local default) and PostgreSQL (compose/k8s) are both
supported; the engine is rebuilt automatically when the configured
``database_url`` changes (which happens in tests via env overrides).
"""

from __future__ import annotations

from collections.abc import Iterator

from sqlalchemy import Engine, create_engine, text
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import StaticPool

from fraud_detection.core.config import get_settings

_engine: Engine | None = None
_engine_url: str | None = None
_session_factory: sessionmaker[Session] | None = None


def _build_engine(url: str, *, echo: bool) -> Engine:
    """Create an engine with dialect-appropriate options."""
    kwargs: dict[str, object] = {"pool_pre_ping": True, "echo": echo}
    if url.startswith("sqlite"):
        # The retraining worker and the request handlers run on different
        # threads; SQLite connections must be shareable across them.
        connect_args: dict[str, object] = {"check_same_thread": False}
        kwargs["connect_args"] = connect_args
        if ":memory:" in url or url.endswith("sqlite://"):
            # A single shared connection so every thread sees the same DB.
            kwargs["poolclass"] = StaticPool
    return create_engine(url, **kwargs)


def get_engine() -> Engine:
    """Return the process-wide engine, (re)creating it lazily when needed."""
    global _engine, _engine_url, _session_factory
    settings = get_settings()
    if _engine is None or _engine_url != settings.database_url:
        if _engine is not None:
            _engine.dispose()
        _engine = _build_engine(settings.database_url, echo=settings.debug)
        _engine_url = settings.database_url
        _session_factory = None
    return _engine


def get_session_factory() -> sessionmaker[Session]:
    """Return the process-wide session factory, creating it lazily."""
    global _session_factory
    engine = get_engine()
    if _session_factory is None:
        _session_factory = sessionmaker(bind=engine, expire_on_commit=False)
    return _session_factory


def get_db_session() -> Iterator[Session]:
    """FastAPI dependency yielding a transactional session."""
    session = get_session_factory()()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


def init_db() -> None:
    """Create all tables that do not exist yet (idempotent).

    Used for local development and tests; production schemas are managed
    by Alembic migrations (see ``scripts/entrypoint.sh``).
    """
    from fraud_detection.db.models import Base

    Base.metadata.create_all(bind=get_engine())


def check_connection() -> bool:
    """Return True when a trivial round-trip to the database succeeds."""
    try:
        with get_engine().connect() as connection:
            connection.execute(text("SELECT 1"))
        return True
    except Exception:  # noqa: BLE001 - any failure means "not reachable"
        return False


def reset_engine() -> None:
    """Dispose the cached engine/session factory (used by the test suite)."""
    global _engine, _engine_url, _session_factory
    if _engine is not None:
        _engine.dispose()
    _engine = None
    _engine_url = None
    _session_factory = None
