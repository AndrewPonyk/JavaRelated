"""Paper broker — bus service that turns approved orders into fills.

In paper mode the platform routes orders here instead of the C++ FIX execution
engine. It tracks the latest price per symbol (from ``market.bars``), matches
orders via :class:`PaperMatchingEngine`, and publishes resulting fills.
"""

from __future__ import annotations

import asyncio
from decimal import Decimal

from trading_common.messaging import MessageBus, topics
from trading_common.models.market import Bar, Order
from trading_common.sim.matching import PaperMatchingEngine
from trading_common.utils import get_logger

log = get_logger(component="paper-broker")


class PaperBroker:
    def __init__(self, bus: MessageBus, engine: PaperMatchingEngine | None = None) -> None:
        self._bus = bus
        self._engine = engine or PaperMatchingEngine()
        self._prices: dict[str, Decimal] = {}
        self.fills_published = 0

    async def run(self) -> None:
        bars = self._bus.subscribe(topics.MARKET_BARS, Bar, group="paper-broker-prices")
        orders = self._bus.subscribe(topics.ORDERS, Order, group="paper-broker")
        await asyncio.gather(self._track_prices(bars), self._handle_orders(orders))

    async def _track_prices(self, sub) -> None:
        async for bar in sub:
            self._prices[bar.symbol] = bar.close

    async def _handle_orders(self, sub) -> None:
        async for order in sub:
            reference = order.limit_price or self._prices.get(order.symbol)
            if reference is None:  # no price to match against; drop (fail closed)
                log.warning("order.no_price", symbol=order.symbol)
                continue
            fill = self._engine.execute(order, reference)
            if fill.is_partial:  # non-marketable limit => not filled
                continue
            await self._bus.publish(topics.FILLS, fill, key=order.symbol)
            self.fills_published += 1
            log.info(
                "fill.published",
                symbol=order.symbol,
                qty=fill.quantity,
                price=str(fill.price),
                correlation_id=str(fill.correlation_id),
            )
