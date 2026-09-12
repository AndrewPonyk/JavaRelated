"""Document ORM model.

Postgres stores **metadata only** — raw bytes live in S3, vectors live in Pinecone.
``status`` is the source of truth surfaced to users while ingestion runs asynchronously.
"""

from __future__ import annotations

import enum

from sqlalchemy import Enum, Index, Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, TimestampMixin, new_uuid


class DocumentStatus(str, enum.Enum):
    PENDING = "pending"  # uploaded, queued for ingestion
    INDEXED = "indexed"  # chunks embedded + upserted to Pinecone
    FAILED = "failed"  # ingestion errored (see DLQ)


class Document(Base, TimestampMixin):
    __tablename__ = "documents"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_uuid)
    tenant_id: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    filename: Mapped[str] = mapped_column(String(512), nullable=False)
    s3_key: Mapped[str] = mapped_column(String(1024), nullable=False)
    doc_type: Mapped[str] = mapped_column(
        String(32), nullable=False, default="general"
    )  # legal | medical | general
    status: Mapped[DocumentStatus] = mapped_column(
        Enum(DocumentStatus), nullable=False, default=DocumentStatus.PENDING
    )
    chunk_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)

    __table_args__ = (
        # Tenant-scoped listing is the hot query path.
        Index("ix_documents_tenant_status", "tenant_id", "status"),
    )
