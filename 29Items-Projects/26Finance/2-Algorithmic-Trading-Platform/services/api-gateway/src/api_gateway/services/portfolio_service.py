"""Read-side service for positions, orders, and aggregate PnL."""

from __future__ import annotations

from decimal import Decimal

from sqlalchemy.ext.asyncio import AsyncSession

from api_gateway.schemas.portfolio import OrderOut, PnLSummary, PositionOut
from trading_common.db import OrderRepository, PositionRepository


class PortfolioService:
    def __init__(self, session: AsyncSession) -> None:
        self._positions = PositionRepository(session)
        self._orders = OrderRepository(session)

    async def list_positions(self) -> list[PositionOut]:
        return [PositionOut.model_validate(r) for r in await self._positions.list()]

    async def list_orders(self, limit: int = 100, offset: int = 0) -> list[OrderOut]:
        return [OrderOut.model_validate(r) for r in await self._orders.list(limit, offset)]

    async def pnl_summary(self) -> PnLSummary:
        positions = await self._positions.list()
        realized = sum((p.realized_pnl for p in positions), Decimal("0"))
        unrealized = sum((p.unrealized_pnl for p in positions), Decimal("0"))
        open_count = sum(1 for p in positions if p.quantity != 0)
        return PnLSummary(
            realized_pnl=realized,
            unrealized_pnl=unrealized,
            total_pnl=realized + unrealized,
            open_positions=open_count,
        )
