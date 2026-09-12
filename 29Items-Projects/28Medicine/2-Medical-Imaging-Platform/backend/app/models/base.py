"""Declarative base + shared mixins + portable column types.

The column types here are written to be portable: native/optimal on PostgreSQL
(production) and still functional on SQLite (used by the test-suite so it runs
without Docker). `Uuid` -> native UUID on PG, CHAR(32) on SQLite; `JSONType` ->
JSONB on PG, JSON elsewhere; `IPType` -> INET on PG, string elsewhere.
"""

from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import JSON, DateTime, String, Uuid, func
from sqlalchemy.dialects.postgresql import INET, JSONB
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

# Portable column types (PG-optimal, SQLite-compatible).
GUID = Uuid(as_uuid=True)
JSONType = JSON().with_variant(JSONB(), "postgresql")  # type: ignore[no-untyped-call]
IPType = String(45).with_variant(INET(), "postgresql")


class Base(DeclarativeBase):
    """Shared declarative base for all ORM models."""


class UUIDMixin:
    id: Mapped[uuid.UUID] = mapped_column(GUID, primary_key=True, default=uuid.uuid4)


class TimestampMixin:
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )
