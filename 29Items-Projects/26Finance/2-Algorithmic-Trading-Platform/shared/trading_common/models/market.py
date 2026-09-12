"""Canonical domain models shared across all services.

Design rules (see docs/TECH-NOTES.md §3.6):
  * Money/prices use ``Decimal`` — never ``float`` — to avoid rounding drift.
  * Every event carries ``correlation_id`` so a fill can be traced back to its tick.
  * Timestamps are timezone-aware UTC.

These models are the contract for Kafka payloads, the FastAPI API, and the DB rows.
Keep them in sync with ``db/migrations`` and ``frontend/src/types``.
"""

from __future__ import annotations

from datetime import UTC, datetime
from decimal import Decimal
from enum import Enum
from uuid import UUID, uuid4

from pydantic import BaseModel, ConfigDict, Field, field_validator


def _utcnow() -> datetime:
    return datetime.now(UTC)


class Side(str, Enum):
    BUY = "BUY"
    SELL = "SELL"


class OrderType(str, Enum):
    MARKET = "MARKET"
    LIMIT = "LIMIT"
    STOP = "STOP"
    STOP_LIMIT = "STOP_LIMIT"


class OrderStatus(str, Enum):
    PENDING = "PENDING"  # created, not yet risk-approved
    APPROVED = "APPROVED"  # passed risk, en route to broker
    REJECTED = "REJECTED"  # risk rejected
    WORKING = "WORKING"  # acknowledged by broker
    PARTIALLY_FILLED = "PARTIALLY_FILLED"
    FILLED = "FILLED"
    CANCELLED = "CANCELLED"


class _Event(BaseModel):
    """Base for all streamed events: immutable, timestamped, traceable."""

    model_config = ConfigDict(frozen=True, extra="forbid")

    correlation_id: UUID = Field(default_factory=uuid4)
    ts_utc: datetime = Field(default_factory=_utcnow)


class Tick(_Event):
    """A normalized market data tick published to ``market.ticks``."""

    symbol: str
    bid: Decimal
    ask: Decimal
    last: Decimal
    volume: int = 0
    exchange_ts: datetime | None = None  # venue timestamp, distinct from ingest ts

    @property
    def mid(self) -> Decimal:
        return (self.bid + self.ask) / Decimal(2)


class Bar(_Event):
    """OHLCV bar — the unit of data the Strategy/backtest API sees (bar-close only)."""

    symbol: str
    open: Decimal
    high: Decimal
    low: Decimal
    close: Decimal
    volume: int
    interval: str = "1m"


class Signal(_Event):
    """A strategy's trade intent, published to ``signals`` for the risk engine."""

    strategy_id: str
    symbol: str
    side: Side
    quantity: int = Field(gt=0)
    target_price: Decimal | None = None  # None => market intent
    confidence: float = Field(default=1.0, ge=0.0, le=1.0)

    @field_validator("symbol")
    @classmethod
    def _upper(cls, v: str) -> str:
        return v.upper()


class Order(_Event):
    """An order on the execution path. Carries an idempotency key for dedupe."""

    order_id: UUID = Field(default_factory=uuid4)
    client_order_id: str  # idempotency key (see TECH-NOTES §3.6)
    strategy_id: str
    symbol: str
    side: Side
    order_type: OrderType
    quantity: int = Field(gt=0)
    limit_price: Decimal | None = None
    status: OrderStatus = OrderStatus.PENDING


class Fill(_Event):
    """An execution report from the broker, published to ``fills``."""

    order_id: UUID
    symbol: str
    side: Side
    quantity: int = Field(gt=0)
    price: Decimal
    commission: Decimal = Decimal("0")
    is_partial: bool = False


class Prediction(_Event):
    """An ML price-direction prediction, published to ``predictions``."""

    symbol: str
    prob_up: float = Field(ge=0.0, le=1.0)
    horizon_secs: int = 60


class RiskDecision(_Event):
    """The risk engine's verdict on a signal, published to ``risk.decisions``."""

    strategy_id: str
    symbol: str
    decision: str  # APPROVED | REJECTED | HALT
    reason: str = ""


class Position(BaseModel):
    """Current net position — a projection folded from fills (lives in Redis + Postgres)."""

    model_config = ConfigDict(extra="forbid")

    symbol: str
    quantity: int = 0  # signed: +long / -short
    avg_price: Decimal = Decimal("0")
    realized_pnl: Decimal = Decimal("0")
    unrealized_pnl: Decimal = Decimal("0")
    updated_ts: datetime = Field(default_factory=_utcnow)
