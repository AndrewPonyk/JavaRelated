"""Tests for the in-memory message bus (publish/subscribe + serialization)."""

from __future__ import annotations

import asyncio
from decimal import Decimal

import pytest

from trading_common.messaging import InMemoryBus, topics
from trading_common.models.market import Side, Signal, Tick


@pytest.mark.asyncio
async def test_publish_subscribe_roundtrip() -> None:
    bus = InMemoryBus()
    await bus.start()
    sub = bus.subscribe(topics.SIGNALS, Signal)

    sig = Signal(strategy_id="s1", symbol="AAPL", side=Side.BUY, quantity=10)
    await bus.publish(topics.SIGNALS, sig, key="s1")

    received = await asyncio.wait_for(anext(aiter(sub)), timeout=1.0)
    assert received.symbol == "AAPL"
    assert received.quantity == 10
    assert received.correlation_id == sig.correlation_id  # survives serialization
    await bus.stop()


@pytest.mark.asyncio
async def test_decimal_survives_serialization() -> None:
    bus = InMemoryBus()
    await bus.start()
    sub = bus.subscribe(topics.MARKET_TICKS, Tick)
    await bus.publish(
        topics.MARKET_TICKS,
        Tick(symbol="AAPL", bid=Decimal("10.01"), ask=Decimal("10.03"), last=Decimal("10.02")),
    )
    tick = await asyncio.wait_for(anext(aiter(sub)), timeout=1.0)
    assert tick.bid == Decimal("10.01")
    assert isinstance(tick.bid, Decimal)
    await bus.stop()


@pytest.mark.asyncio
async def test_fanout_to_multiple_subscribers() -> None:
    bus = InMemoryBus()
    await bus.start()
    sub_a = bus.subscribe(topics.SIGNALS, Signal, group="a")
    sub_b = bus.subscribe(topics.SIGNALS, Signal, group="b")
    await bus.publish(
        topics.SIGNALS, Signal(strategy_id="s", symbol="X", side=Side.SELL, quantity=1)
    )

    a = await asyncio.wait_for(anext(aiter(sub_a)), timeout=1.0)
    b = await asyncio.wait_for(anext(aiter(sub_b)), timeout=1.0)
    assert a.symbol == b.symbol == "X"
    await bus.stop()
