"""Transactional outbox for PG -> ES synchronization (ARCHITECTURE §2.3).

Catalog writes insert a row here in the SAME transaction as the product change;
the outbox worker (services/outbox_worker.py) drains pending rows into the index.
This is what makes the search index crash-consistent with PostgreSQL.
"""

from datetime import datetime
from typing import Literal

from sqlalchemy import BigInteger, DateTime, Integer, String, Text, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base

OutboxOp = Literal["upsert", "delete"]


class IndexOutbox(Base):
    __tablename__ = "index_outbox"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    product_id: Mapped[str] = mapped_column(UUID(as_uuid=False), nullable=False)
    op: Mapped[str] = mapped_column(String(8), nullable=False)  # upsert | delete
    attempts: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    last_error: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    processed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
