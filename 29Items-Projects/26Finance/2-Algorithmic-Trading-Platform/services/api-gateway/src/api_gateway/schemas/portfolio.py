"""Schemas for positions, orders, and PnL responses.

Money is serialized as a string to preserve Decimal precision over JSON, matching
the frontend types (frontend/src/types/strategy.ts).
"""

from __future__ import annotations

from datetime import datetime
from decimal import Decimal
from typing import Annotated
from uuid import UUID

from pydantic import BaseModel, ConfigDict, PlainSerializer

# Decimal -> string on serialization (preserve precision).
DecimalStr = Annotated[Decimal, PlainSerializer(lambda d: format(d, "f"), return_type=str)]


class PositionOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    symbol: str
    quantity: int
    avg_price: DecimalStr
    realized_pnl: DecimalStr
    unrealized_pnl: DecimalStr
    updated_at: datetime


class OrderOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    client_order_id: str
    strategy_id: UUID
    symbol: str
    side: str
    order_type: str
    quantity: int
    limit_price: DecimalStr | None
    status: str
    created_at: datetime


class PnLSummary(BaseModel):
    realized_pnl: DecimalStr
    unrealized_pnl: DecimalStr
    total_pnl: DecimalStr
    open_positions: int
