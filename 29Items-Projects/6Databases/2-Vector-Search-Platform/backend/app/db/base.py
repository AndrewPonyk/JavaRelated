"""Declarative base + shared metadata.

Import every model module somewhere on this ``Base`` so Alembic autogenerate sees them.
"""

from __future__ import annotations

from sqlalchemy.orm import DeclarativeBase


class Base(DeclarativeBase):
    """Project-wide SQLAlchemy declarative base."""
