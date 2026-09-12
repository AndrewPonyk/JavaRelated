"""SQLAlchemy 2.0 models. Schema changes ship as Alembic migrations —
never edit the DB by hand; see backend/migrations/.

Types are backend-portable: PostgreSQL gets native UUID/JSONB (see the
migrations), while tests run the identical models on SQLite.
"""

from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import JSON, Boolean, DateTime, ForeignKey, Index, String, Uuid, func
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship

JSONPayload = JSON().with_variant(JSONB(), "postgresql")


class Base(DeclarativeBase):
    pass


class User(Base):
    __tablename__ = "users"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    email: Mapped[str] = mapped_column(String(320), unique=True, index=True)
    password_hash: Mapped[str] = mapped_column(String(256))
    role: Mapped[str] = mapped_column(String(32), default="student")  # student|instructor|admin
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

    computations: Mapped[list[Computation]] = relationship(
        back_populates="owner", cascade="all, delete-orphan"
    )
    notebooks: Mapped[list[Notebook]] = relationship(
        back_populates="owner", cascade="all, delete-orphan"
    )
    refresh_tokens: Mapped[list[RefreshToken]] = relationship(
        back_populates="user", cascade="all, delete-orphan"
    )


class RefreshToken(Base):
    """Server-side record of an issued refresh token (revocation + rotation).

    The jti claim is the primary key; revoking a row invalidates the token no
    matter who holds it (docs/ARCHITECTURE.md §2.5).
    """

    __tablename__ = "refresh_tokens"

    jti: Mapped[str] = mapped_column(String(64), primary_key=True)
    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid, ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    revoked: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

    user: Mapped[User] = relationship(back_populates="refresh_tokens")


class Computation(Base):
    """One submitted operation (solve/integrate/plot/…) and its outcome.

    JSONB payloads keep the hot path join-free (docs/ARCHITECTURE.md §2.4).
    """

    __tablename__ = "computations"
    __table_args__ = (
        # The list endpoint's exact access path: filter by owner, newest first.
        Index("ix_computations_owner_created", "owner_id", "created_at"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    owner_id: Mapped[uuid.UUID] = mapped_column(
        Uuid, ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    title: Mapped[str] = mapped_column(String(200))
    kind: Mapped[str] = mapped_column(String(32))  # symbolic_solve|integral|ode|plot|ml_classify
    status: Mapped[str] = mapped_column(String(16), default="queued", index=True)
    input_payload: Mapped[dict] = mapped_column(JSONPayload)
    result_payload: Mapped[dict | None] = mapped_column(JSONPayload, nullable=True)
    error_code: Mapped[str | None] = mapped_column(String(64), nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), index=True
    )
    completed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    owner: Mapped[User] = relationship(back_populates="computations")


class Notebook(Base):
    """Metadata for a Jupyter notebook; the .ipynb itself lives in S3."""

    __tablename__ = "notebooks"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    owner_id: Mapped[uuid.UUID] = mapped_column(
        Uuid, ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    title: Mapped[str] = mapped_column(String(200))
    s3_key: Mapped[str] = mapped_column(String(1024), unique=True)
    is_template: Mapped[bool] = mapped_column(Boolean, default=False)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now()
    )

    owner: Mapped[User] = relationship(back_populates="notebooks")
