"""ORM models — mirror db/migrations (the production DDL source of truth).

Money is ``Numeric`` (maps to Python ``Decimal``); UUIDs use the cross-dialect
``Uuid`` type; ``symbols``/``params`` use ``JSON`` so the same models run on
SQLite (dev/test) and PostgreSQL (prod).
"""

from __future__ import annotations

from datetime import UTC, datetime
from decimal import Decimal
from uuid import UUID, uuid4

from sqlalchemy import (
    JSON,
    BigInteger,
    Boolean,
    DateTime,
    ForeignKey,
    Integer,
    Numeric,
    String,
    Uuid,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from trading_common.db.base import Base

# BIGINT on PostgreSQL, but INTEGER on SQLite so it aliases rowid and
# autoincrements (SQLite only autoincrements an INTEGER PRIMARY KEY).
AutoBigInt = BigInteger().with_variant(Integer, "sqlite")


def _utcnow() -> datetime:
    return datetime.now(UTC)


class InstrumentRow(Base):
    __tablename__ = "instrument"

    symbol: Mapped[str] = mapped_column(String(32), primary_key=True)
    description: Mapped[str] = mapped_column(String(256), nullable=False, default="")
    asset_class: Mapped[str] = mapped_column(String(16), nullable=False, default="EQUITY")
    tick_size: Mapped[Decimal] = mapped_column(
        Numeric(18, 8), nullable=False, default=Decimal("0.01")
    )
    lot_size: Mapped[int] = mapped_column(Integer, nullable=False, default=1)
    is_active: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)


class AccountRow(Base):
    __tablename__ = "account"

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    broker: Mapped[str] = mapped_column(String(64), nullable=False)
    external_ref: Mapped[str] = mapped_column(String(128), nullable=False)
    base_currency: Mapped[str] = mapped_column(String(8), nullable=False, default="USD")
    is_live: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)


class StrategyRow(Base):
    __tablename__ = "strategy"

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    name: Mapped[str] = mapped_column(String(64), nullable=False, unique=True)
    klass: Mapped[str] = mapped_column("klass", String(256), nullable=False)
    symbols: Mapped[list[str]] = mapped_column(JSON, nullable=False, default=list)
    params: Mapped[dict] = mapped_column(JSON, nullable=False, default=dict)
    state: Mapped[str] = mapped_column(String(16), nullable=False, default="DRAFT")
    max_position_qty: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    max_order_notional: Mapped[Decimal] = mapped_column(
        Numeric(18, 2), nullable=False, default=Decimal("0")
    )
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=_utcnow, onupdate=_utcnow
    )

    orders: Mapped[list[OrderRow]] = relationship(back_populates="strategy")


class OrderRow(Base):
    __tablename__ = "order"

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    # Idempotency key — globally unique so a redelivered order is not double-placed.
    client_order_id: Mapped[str] = mapped_column(
        String(64), nullable=False, unique=True, index=True
    )
    account_id: Mapped[UUID | None] = mapped_column(Uuid, ForeignKey("account.id"), nullable=True)
    strategy_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("strategy.id"), nullable=False, index=True
    )
    symbol: Mapped[str] = mapped_column(String(32), nullable=False, index=True)
    side: Mapped[str] = mapped_column(String(4), nullable=False)
    order_type: Mapped[str] = mapped_column(String(12), nullable=False, default="MARKET")
    quantity: Mapped[int] = mapped_column(BigInteger, nullable=False)
    limit_price: Mapped[Decimal | None] = mapped_column(Numeric(18, 4), nullable=True)
    status: Mapped[str] = mapped_column(String(20), nullable=False, default="PENDING", index=True)
    correlation_id: Mapped[UUID] = mapped_column(Uuid, nullable=False, default=uuid4)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=_utcnow, onupdate=_utcnow
    )

    strategy: Mapped[StrategyRow] = relationship(back_populates="orders")
    fills: Mapped[list[FillRow]] = relationship(back_populates="order")


class FillRow(Base):
    __tablename__ = "fill"

    id: Mapped[int] = mapped_column(AutoBigInt, primary_key=True, autoincrement=True)
    order_id: Mapped[UUID] = mapped_column(Uuid, ForeignKey("order.id"), nullable=False, index=True)
    symbol: Mapped[str] = mapped_column(String(32), nullable=False, index=True)
    side: Mapped[str] = mapped_column(String(4), nullable=False)
    quantity: Mapped[int] = mapped_column(BigInteger, nullable=False)
    price: Mapped[Decimal] = mapped_column(Numeric(18, 4), nullable=False)
    commission: Mapped[Decimal] = mapped_column(
        Numeric(18, 4), nullable=False, default=Decimal("0")
    )
    is_partial: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    executed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)

    order: Mapped[OrderRow] = relationship(back_populates="fills")


class SignalRow(Base):
    __tablename__ = "signal"

    id: Mapped[int] = mapped_column(AutoBigInt, primary_key=True, autoincrement=True)
    strategy_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("strategy.id"), nullable=False, index=True
    )
    symbol: Mapped[str] = mapped_column(String(32), nullable=False, index=True)
    side: Mapped[str] = mapped_column(String(4), nullable=False)
    quantity: Mapped[int] = mapped_column(BigInteger, nullable=False)
    target_price: Mapped[Decimal | None] = mapped_column(Numeric(18, 4), nullable=True)
    confidence: Mapped[float] = mapped_column(Numeric(5, 4), nullable=False, default=1.0)
    correlation_id: Mapped[UUID] = mapped_column(Uuid, nullable=False, default=uuid4)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)


class PositionRow(Base):
    __tablename__ = "position"

    symbol: Mapped[str] = mapped_column(String(32), primary_key=True)
    quantity: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    avg_price: Mapped[Decimal] = mapped_column(Numeric(18, 4), nullable=False, default=Decimal("0"))
    realized_pnl: Mapped[Decimal] = mapped_column(
        Numeric(18, 4), nullable=False, default=Decimal("0")
    )
    unrealized_pnl: Mapped[Decimal] = mapped_column(
        Numeric(18, 4), nullable=False, default=Decimal("0")
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=_utcnow, onupdate=_utcnow
    )
