from datetime import datetime

from sqlalchemy import BigInteger, DateTime, Integer, Numeric, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class Address(Base):
    """Maps the ``addresses`` table from migration 001.

    The ``geom`` geography column is ``GENERATED ALWAYS`` in PostGIS from
    latitude/longitude, so it is intentionally not mapped here; spatial reads go
    through raw SQL in the repository.
    """

    __tablename__ = "addresses"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    formatted_address: Mapped[str] = mapped_column(Text, nullable=False)
    normalized_address: Mapped[str] = mapped_column(Text, nullable=False)
    source: Mapped[str] = mapped_column(String(50), nullable=False)
    provider_place_id: Mapped[str | None] = mapped_column(Text, nullable=True)
    confidence: Mapped[float] = mapped_column(Numeric(5, 4), nullable=False, default=0)
    latitude: Mapped[float] = mapped_column(nullable=False)
    longitude: Mapped[float] = mapped_column(nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )


class AddressLookupEvent(Base):
    """Audit row written for every forward/reverse lookup (table from migration 001)."""

    __tablename__ = "address_lookup_events"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    query: Mapped[str] = mapped_column(Text, nullable=False)
    normalized_query: Mapped[str] = mapped_column(Text, nullable=False)
    address_id: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    match_status: Mapped[str] = mapped_column(String(30), nullable=False)
    latency_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
