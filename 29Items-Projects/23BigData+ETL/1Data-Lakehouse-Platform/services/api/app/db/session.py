"""Engine and session management. Engine is created lazily so importing the app
(e.g. in tests with an overridden dependency) never opens a connection."""

from __future__ import annotations

from collections.abc import Iterator
from functools import lru_cache

from sqlalchemy import create_engine
from sqlalchemy.engine import Engine
from sqlalchemy.orm import Session, sessionmaker

from app.core.config import get_settings


@lru_cache
def get_engine() -> Engine:
    url = get_settings().database_url
    connect_args = {}
    if url.startswith("postgresql"):
        # Fail fast instead of hanging readiness probes on an unreachable DB.
        connect_args["connect_timeout"] = 3
    return create_engine(url, pool_pre_ping=True, connect_args=connect_args)


def get_db() -> Iterator[Session]:
    """FastAPI dependency — one session per request, always closed.

    Tests override this via `app.dependency_overrides[get_db]` (see tests).
    """
    factory = sessionmaker(bind=get_engine(), autoflush=False, expire_on_commit=False)
    session = factory()
    try:
        yield session
    finally:
        session.close()
