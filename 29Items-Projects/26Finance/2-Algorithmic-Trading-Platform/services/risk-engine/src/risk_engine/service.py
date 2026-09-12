"""Risk-engine service: the pre-trade gate wired onto the bus.

Consumes ``signals``, evaluates them against limits (reading live positions from a
PositionStore), and publishes either an APPROVED :class:`Order` to ``orders`` or a
REJECTED :class:`RiskDecision` to ``risk.decisions``. Fail closed: no price / over
limit => no order (ARCHITECTURE §2.6).
"""

from __future__ import annotations

import asyncio
from decimal import Decimal

from risk_engine.limits import RiskEngine, RiskLimits
from trading_common.messaging import MessageBus, topics
from trading_common.models.market import (
    Bar,
    Order,
    OrderStatus,
    OrderType,
    RiskDecision,
    Signal,
)
from trading_common.portfolio import InMemoryPositionStore, PositionStore
from trading_common.utils import get_logger

log = get_logger(component="risk-engine")


def make_order_from_signal(signal: Signal) -> Order:
    """Translate an approved signal into an order with an idempotency key."""
    order_type = OrderType.LIMIT if signal.target_price is not None else OrderType.MARKET
    return Order(
        correlation_id=signal.correlation_id,
        client_order_id=f"{signal.strategy_id}:{signal.correlation_id}",
        strategy_id=signal.strategy_id,
        symbol=signal.symbol,
        side=signal.side,
        order_type=order_type,
        quantity=signal.quantity,
        limit_price=signal.target_price,
        status=OrderStatus.APPROVED,
    )


class RiskService:
    def __init__(
        self,
        bus: MessageBus,
        limits: RiskLimits,
        position_store: PositionStore | None = None,
    ) -> None:
        self._bus = bus
        self._engine = RiskEngine(limits)
        self._store = position_store or InMemoryPositionStore()
        self._prices: dict[str, Decimal] = {}
        self.approved = 0
        self.rejected = 0

    async def run(self) -> None:
        bars = self._bus.subscribe(topics.MARKET_BARS, Bar, group="risk-engine-prices")
        signals = self._bus.subscribe(topics.SIGNALS, Signal, group="risk-engine")
        await asyncio.gather(self._track_prices(bars), self._handle_signals(signals))

    async def _track_prices(self, sub) -> None:
        async for bar in sub:
            self._prices[bar.symbol] = bar.close

    async def _handle_signals(self, sub) -> None:
        async for signal in sub:
            await self._evaluate(signal)

    async def _evaluate(self, signal: Signal) -> None:
        price = signal.target_price or self._prices.get(signal.symbol)
        if price is None:  # fail closed: cannot size without a price
            await self._reject(signal, "no reference price available")
            return

        position = await self._store.get(signal.symbol)
        positions = await self._store.all()
        realized = sum((p.realized_pnl for p in positions), Decimal("0"))

        result = self._engine.check(
            signal,
            reference_price=price,
            current_position=position,
            realized_daily_pnl=realized,
        )
        if result.approved:
            self.approved += 1  # count before publish so observers see a consistent state
            order = make_order_from_signal(signal)
            log.info(
                "order.approved",
                symbol=signal.symbol,
                qty=signal.quantity,
                correlation_id=str(signal.correlation_id),
            )
            await self._bus.publish(topics.ORDERS, order, key=signal.symbol)
        else:
            await self._reject(signal, result.reason)

    async def _reject(self, signal: Signal, reason: str) -> None:
        self.rejected += 1
        decision = RiskDecision(
            correlation_id=signal.correlation_id,
            strategy_id=signal.strategy_id,
            symbol=signal.symbol,
            decision="REJECTED",
            reason=reason,
        )
        log.warning(
            "order.rejected",
            symbol=signal.symbol,
            reason=reason,
            correlation_id=str(signal.correlation_id),
        )
        await self._bus.publish(topics.RISK_DECISIONS, decision, key=signal.strategy_id)
