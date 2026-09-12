"""SQLAlchemy declarative base.

The hand-written SQL in ``db/migrations`` remains the source of truth for the
PostgreSQL production schema (PROJECT-PLAN §2.1). These ORM models mirror it and
are used directly (via ``create_all``) for SQLite in dev/test, so the suite runs
with no external database. Column types are chosen to be portable across both
dialects (``Uuid``, ``JSON``, ``Numeric``).
"""

from __future__ import annotations

from sqlalchemy.orm import DeclarativeBase


class Base(DeclarativeBase):
    """Declarative base for all ORM models."""
