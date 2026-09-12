"""Query log ORM model.

Persists every Q&A interaction for auditing, cost tracking, and building eval datasets
(question + retrieved citations + answer + optional user feedback).
"""

from __future__ import annotations

from sqlalchemy import JSON, Index, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, TimestampMixin, new_uuid


class QueryLog(Base, TimestampMixin):
    __tablename__ = "query_logs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_uuid)
    tenant_id: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    question: Mapped[str] = mapped_column(Text, nullable=False)
    answer: Mapped[str] = mapped_column(Text, nullable=False)
    # List of {document_id, chunk_index, score} dicts.
    citations: Mapped[list] = mapped_column(JSON, nullable=False, default=list)
    model_id: Mapped[str] = mapped_column(String(128), nullable=False)
    latency_ms: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    # -1 / 0 / +1 user feedback for eval curation.
    feedback: Mapped[int] = mapped_column(Integer, nullable=False, default=0)

    __table_args__ = (
        # Time-ordered analytics / eval-dataset queries are tenant-scoped.
        Index("ix_query_logs_tenant_created", "tenant_id", "created_at"),
    )
