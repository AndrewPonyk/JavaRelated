"""Tests for the risk-engine bus service (signal -> order / rejection)."""

from __future__ import annotations

import asyncio
import contextlib
from decimal import Decimal

import pytest
from risk_engine.limits import RiskLimits
from risk_engine.service import RiskService, make_order_from_signal

from trading_common.messaging import InMemoryBus, topics
from trading_common.models.market import Bar, Order, RiskDecision, Side, Signal
from trading_common.portfolio import InMemoryPositionStore

LIMITS = RiskLimits(
    max_position_qty=1000,
    max_order_notional=Decimal("250000"),
    max_daily_loss=Decimal("50000"),
)


def _bar(symbol: str, price: float) -> Bar:
    c = Decimal(str(price))
    return Bar(symbol=symbol, open=c, high=c, low=c, close=c, volume=1)


async def _next(sub, timeout: float = 1.0):
    return await asyncio.wait_for(anext(aiter(sub)), timeout=timeout)


def test_make_order_from_signal_sets_idempotency_key() -> None:
    sig = Signal(strategy_id="s", symbol="AAPL", side=Side.BUY, quantity=10)
    order = make_order_from_signal(sig)
    assert order.client_order_id == f"s:{sig.correlation_id}"
    assert order.correlation_id == sig.correlation_id
    assert order.status.value == "APPROVED"


@pytest.mark.asyncio
async def test_approved_signal_becomes_order() -> None:
    bus = InMemoryBus()
    await bus.start()
    svc = RiskService(bus, LIMITS, InMemoryPositionStore())
    orders = bus.subscribe(topics.ORDERS, Order, group="test")
    run = asyncio.ensure_future(svc.run())
    await asyncio.sleep(0)

    await bus.publish(topics.MARKET_BARS, _bar("AAPL", 100))  # establish price
    await asyncio.sleep(0.01)
    await bus.publish(
        topics.SIGNALS, Signal(strategy_id="s", symbol="AAPL", side=Side.BUY, quantity=100)
    )

    order = await _next(orders)
    assert order.symbol == "AAPL" and order.quantity == 100
    assert svc.approved == 1
    await bus.stop()
    with contextlib.suppress(asyncio.CancelledError):
        await run


@pytest.mark.asyncio
async def test_signal_without_price_is_rejected() -> None:
    bus = InMemoryBus()
    await bus.start()
    svc = RiskService(bus, LIMITS, InMemoryPositionStore())
    decisions = bus.subscribe(topics.RISK_DECISIONS, RiskDecision, group="test")
    run = asyncio.ensure_future(svc.run())
    await asyncio.sleep(0)

    # No bar published => no reference price => fail closed.
    await bus.publish(
        topics.SIGNALS, Signal(strategy_id="s", symbol="AAPL", side=Side.BUY, quantity=10)
    )
    decision = await _next(decisions)
    assert decision.decision == "REJECTED"
    assert "price" in decision.reason
    await bus.stop()
    with contextlib.suppress(asyncio.CancelledError):
        await run


@pytest.mark.asyncio
async def test_oversized_order_rejected() -> None:
    bus = InMemoryBus()
    await bus.start()
    svc = RiskService(bus, LIMITS, InMemoryPositionStore())
    decisions = bus.subscribe(topics.RISK_DECISIONS, RiskDecision, group="test")
    run = asyncio.ensure_future(svc.run())
    await asyncio.sleep(0)

    await bus.publish(topics.MARKET_BARS, _bar("AAPL", 5000))  # 100 * 5000 = 500k > 250k cap
    await asyncio.sleep(0.01)
    await bus.publish(
        topics.SIGNALS, Signal(strategy_id="s", symbol="AAPL", side=Side.BUY, quantity=100)
    )

    decision = await _next(decisions)
    assert decision.decision == "REJECTED"
    assert "notional" in decision.reason
    assert svc.rejected == 1
    await bus.stop()
    with contextlib.suppress(asyncio.CancelledError):
        await run
