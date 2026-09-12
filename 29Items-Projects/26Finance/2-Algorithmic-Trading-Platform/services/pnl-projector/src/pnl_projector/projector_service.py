"""pnl-projector service — folds fills into positions and PnL.

Consumes ``fills`` (and ``market.bars`` for marks), updates the live PositionStore,
and persists the projection to PostgreSQL so the api-gateway can serve it
(ARCHITECTURE §2.3.1: fills -> position/PnL).
"""

from __future__ import annotations

import asyncio

from sqlalchemy.ext.asyncio import async_sessionmaker

from trading_common.db import PositionRepository, session_scope
from trading_common.messaging import MessageBus, topics
from trading_common.models.market import Bar, Fill, Position
from trading_common.portfolio import PositionStore, Projector
from trading_common.utils import get_logger

log = get_logger(component="pnl-projector")


class ProjectorService:
    def __init__(
        self,
        bus: MessageBus,
        store: PositionStore,
        session_factory: async_sessionmaker | None = None,
    ) -> None:
        self._bus = bus
        self._projector = Projector(store)
        self._session_factory = session_factory
        self.fills_processed = 0

    async def run(self) -> None:
        fills = self._bus.subscribe(topics.FILLS, Fill, group="pnl-projector")
        bars = self._bus.subscribe(topics.MARKET_BARS, Bar, group="pnl-projector-marks")
        await asyncio.gather(self._handle_fills(fills), self._handle_marks(bars))

    async def _handle_fills(self, sub) -> None:
        async for fill in sub:
            position = await self._projector.on_fill(fill)
            self.fills_processed += 1
            await self._persist(position)
            log.info(
                "position.updated",
                symbol=position.symbol,
                qty=position.quantity,
                realized=str(position.realized_pnl),
            )

    async def _handle_marks(self, sub) -> None:
        async for bar in sub:
            await self._projector.on_mark(bar.symbol, bar.close)

    async def _persist(self, position: Position) -> None:
        if self._session_factory is None:
            return
        async with session_scope(self._session_factory) as session:
            await PositionRepository(session).upsert(
                position.symbol,
                position.quantity,
                position.avg_price,
                position.realized_pnl,
                position.unrealized_pnl,
            )
