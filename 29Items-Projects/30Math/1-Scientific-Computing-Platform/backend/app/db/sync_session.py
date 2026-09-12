"""Synchronous SQLAlchemy wiring for Celery workers (and Alembic).

Workers are plain synchronous code (docs/ARCHITECTURE.md §2.2: "async in API,
sync in worker"), so they get their own engine derived from the same
DATABASE_URL. psycopg3 serves both modes through one URL; the sqlite test URL
just drops its async driver suffix.
"""

from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager
from functools import lru_cache

from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker

from app.core.config import get_settings


def sync_database_url() -> str:
    return get_settings().database_url.replace("+aiosqlite", "")


@lru_cache
def get_sync_engine():
    url = sync_database_url()
    if url.startswith("sqlite"):
        return create_engine(url, connect_args={"timeout": 30})
    return create_engine(url, pool_pre_ping=True)


@lru_cache
def get_sync_sessionmaker() -> sessionmaker[Session]:
    return sessionmaker(get_sync_engine(), expire_on_commit=False)


@contextmanager
def sync_session() -> Iterator[Session]:
    session = get_sync_sessionmaker()()
    try:
        yield session
    finally:
        session.close()
