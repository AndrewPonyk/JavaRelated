"""Async repositories — the only place SQL/ORM queries live.

Shared by the api-gateway (CRUD/read) and the pnl-projector (writes from fills),
so persistence logic exists once.
"""

from __future__ import annotations

from decimal import Decimal
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from trading_common.db.models import (
    FillRow,
    OrderRow,
    PositionRow,
    SignalRow,
    StrategyRow,
)


class StrategyRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._s = session

    async def list(self) -> list[StrategyRow]:
        result = await self._s.execute(select(StrategyRow).order_by(StrategyRow.created_at))
        return list(result.scalars().all())

    async def get(self, strategy_id: UUID) -> StrategyRow | None:
        return await self._s.get(StrategyRow, strategy_id)

    async def get_by_name(self, name: str) -> StrategyRow | None:
        result = await self._s.execute(select(StrategyRow).where(StrategyRow.name == name))
        return result.scalar_one_or_none()

    async def add(self, row: StrategyRow) -> StrategyRow:
        self._s.add(row)
        await self._s.flush()
        return row

    async def delete(self, row: StrategyRow) -> None:
        await self._s.delete(row)
        await self._s.flush()

    async def list_active(self) -> list[StrategyRow]:
        result = await self._s.execute(
            select(StrategyRow).where(StrategyRow.state.in_(("PAPER", "LIVE")))
        )
        return list(result.scalars().all())


class OrderRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._s = session

    async def add(self, row: OrderRow) -> OrderRow:
        self._s.add(row)
        await self._s.flush()
        return row

    async def get(self, order_id: UUID) -> OrderRow | None:
        return await self._s.get(OrderRow, order_id)

    async def get_by_client_id(self, client_order_id: str) -> OrderRow | None:
        result = await self._s.execute(
            select(OrderRow).where(OrderRow.client_order_id == client_order_id)
        )
        return result.scalar_one_or_none()

    async def list(self, limit: int = 100, offset: int = 0) -> list[OrderRow]:
        result = await self._s.execute(
            select(OrderRow).order_by(OrderRow.created_at.desc()).limit(limit).offset(offset)
        )
        return list(result.scalars().all())


class FillRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._s = session

    async def add(self, row: FillRow) -> FillRow:
        self._s.add(row)
        await self._s.flush()
        return row

    async def list(self, limit: int = 100, offset: int = 0) -> list[FillRow]:
        result = await self._s.execute(
            select(FillRow).order_by(FillRow.executed_at.desc()).limit(limit).offset(offset)
        )
        return list(result.scalars().all())


class SignalRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._s = session

    async def add(self, row: SignalRow) -> SignalRow:
        self._s.add(row)
        await self._s.flush()
        return row


class PositionRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._s = session

    async def get(self, symbol: str) -> PositionRow | None:
        return await self._s.get(PositionRow, symbol)

    async def list(self) -> list[PositionRow]:
        result = await self._s.execute(select(PositionRow).order_by(PositionRow.symbol))
        return list(result.scalars().all())

    async def upsert(
        self,
        symbol: str,
        quantity: int,
        avg_price: Decimal,
        realized_pnl: Decimal,
        unrealized_pnl: Decimal = Decimal("0"),
    ) -> PositionRow:
        row = await self._s.get(PositionRow, symbol)
        if row is None:
            row = PositionRow(symbol=symbol)
            self._s.add(row)
        row.quantity = quantity
        row.avg_price = avg_price
        row.realized_pnl = realized_pnl
        row.unrealized_pnl = unrealized_pnl
        await self._s.flush()
        return row
