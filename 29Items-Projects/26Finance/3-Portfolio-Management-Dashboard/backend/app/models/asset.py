from __future__ import annotations

from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, Index, Numeric, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, TimestampMixin


class Asset(Base, TimestampMixin):
    __tablename__ = "assets"

    id: Mapped[int] = mapped_column(primary_key=True)
    symbol: Mapped[str] = mapped_column(String(32), unique=True, index=True, nullable=False)
    name: Mapped[str] = mapped_column(String(255), nullable=False)
    asset_class: Mapped[str] = mapped_column(String(32), default="equity", nullable=False)
    currency: Mapped[str] = mapped_column(String(3), default="USD", nullable=False)

    price_bars: Mapped[list[PriceBar]] = relationship(
        back_populates="asset", cascade="all, delete-orphan"
    )


class PriceBar(Base):
    """A single OHLCV daily bar for an asset. The core time series for analytics."""

    __tablename__ = "price_bars"
    __table_args__ = (Index("ix_price_bars_asset_ts", "asset_id", "ts", unique=True),)

    id: Mapped[int] = mapped_column(primary_key=True)
    asset_id: Mapped[int] = mapped_column(
        ForeignKey("assets.id", ondelete="CASCADE"), nullable=False
    )
    ts: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    open: Mapped[float] = mapped_column(Numeric(18, 6))
    high: Mapped[float] = mapped_column(Numeric(18, 6))
    low: Mapped[float] = mapped_column(Numeric(18, 6))
    close: Mapped[float] = mapped_column(Numeric(18, 6), nullable=False)
    volume: Mapped[float | None] = mapped_column(Numeric(20, 2))

    asset: Mapped[Asset] = relationship(back_populates="price_bars")
