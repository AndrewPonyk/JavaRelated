"""Catalog metadata schema (SQLAlchemy 2.0, mirrored by Alembic migrations).

Portable column types (Uuid, JSON) so tests can run on SQLite; the Postgres
migration uses native UUID/JSONB.
"""

from __future__ import annotations

import enum
import uuid
from datetime import datetime

from sqlalchemy import (
    JSON,
    BigInteger,
    DateTime,
    Enum,
    ForeignKey,
    String,
    Text,
    UniqueConstraint,
    Uuid,
    func,
)
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


class Base(DeclarativeBase):
    pass


class DatasetLayer(str, enum.Enum):
    bronze = "bronze"
    silver = "silver"
    gold = "gold"


class Dataset(Base):
    """A governed table in the lakehouse — the unit of ownership and audit."""

    __tablename__ = "datasets"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    name: Mapped[str] = mapped_column(String(120), unique=True, index=True)
    layer: Mapped[DatasetLayer] = mapped_column(
        Enum(DatasetLayer, name="dataset_layer"),
        index=True,  # list endpoint filters by layer
    )
    description: Mapped[str | None] = mapped_column(Text, default=None)
    owner_email: Mapped[str] = mapped_column(String(254))
    s3_path: Mapped[str] = mapped_column(String(1024))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now()
    )

    versions: Mapped[list[DatasetVersion]] = relationship(
        back_populates="dataset", cascade="all, delete-orphan", order_by="DatasetVersion.version"
    )


class DatasetVersion(Base):
    """Schema snapshot per contract change — additive-only evolution is enforced in review."""

    __tablename__ = "dataset_versions"
    __table_args__ = (UniqueConstraint("dataset_id", "version", name="uq_dataset_version"),)

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    dataset_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("datasets.id", ondelete="CASCADE"), index=True
    )
    version: Mapped[int]
    schema_json: Mapped[dict] = mapped_column(JSON)
    row_count: Mapped[int | None] = mapped_column(BigInteger, default=None)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

    dataset: Mapped[Dataset] = relationship(back_populates="versions")


class AuditEvent(Base):
    """Application-level audit trail (complements CloudTrail + Delta history)."""

    __tablename__ = "audit_events"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    occurred_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), index=True
    )
    actor: Mapped[str] = mapped_column(String(254), index=True)  # audit filter field
    action: Mapped[str] = mapped_column(String(64))  # e.g. dataset.created
    entity_type: Mapped[str] = mapped_column(String(64))
    entity_id: Mapped[str] = mapped_column(String(64), index=True)
    details: Mapped[dict | None] = mapped_column(JSON, default=None)
