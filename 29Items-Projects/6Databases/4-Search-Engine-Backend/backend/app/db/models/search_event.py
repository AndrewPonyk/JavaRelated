"""Search interaction log — the raw signal for LTR training (ml/ltr/build_judgments.py)
and for the nightly popularity recompute. Deliberately PII-free: session pseudonym only.
"""

from datetime import datetime
from typing import Any

from sqlalchemy import BigInteger, DateTime, Integer, String, Text, func
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class SearchEvent(Base):
    __tablename__ = "search_events"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    query: Mapped[str] = mapped_column(Text)
    normalized_query: Mapped[str] = mapped_column(String(512), index=True)
    filters: Mapped[dict[str, Any]] = mapped_column(JSONB, default=dict)
    results_count: Mapped[int] = mapped_column(Integer, default=0)
    # First-page product ids shown for this query — the impression log that makes
    # per-product CTR (and therefore LTR judgments) computable.
    shown_product_ids: Mapped[list[str] | None] = mapped_column(JSONB, nullable=True)
    # Click/conversion feedback; nullable — pure impressions have no click.
    clicked_product_id: Mapped[str | None] = mapped_column(UUID(as_uuid=False))
    clicked_position: Mapped[int | None] = mapped_column(Integer)
    session_id: Mapped[str] = mapped_column(String(64), index=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), index=True
    )
