"""Tests for the pnl-projector service (fills -> position, persisted to DB)."""

from __future__ import annotations

import asyncio
import contextlib
from decimal import Decimal
from uuid import uuid4

import pytest
from pnl_projector.projector_service import ProjectorService

from trading_common.db import PositionRepository, session_scope
from trading_common.messaging import InMemoryBus, topics
from trading_common.models.market import Bar, Fill, Side
from trading_common.portfolio import InMemoryPositionStore


def _fill(side: Side, qty: int, price: str) -> Fill:
    return Fill(order_id=uuid4(), symbol="AAPL", side=side, quantity=qty, price=Decimal(price))


@pytest.mark.asyncio
async def test_fill_updates_store_and_db(session_factory) -> None:
    bus = InMemoryBus()
    await bus.start()
    store = InMemoryPositionStore()
    svc = ProjectorService(bus, store, session_factory)
    run = asyncio.ensure_future(svc.run())
    await asyncio.sleep(0)

    await bus.publish(topics.FILLS, _fill(Side.BUY, 100, "150"))
    await asyncio.sleep(0.05)

    pos = await store.get("AAPL")
    assert pos.quantity == 100 and pos.avg_price == Decimal("150")

    async with session_scope(session_factory) as session:
        row = await PositionRepository(session).get("AAPL")
        assert row is not None and row.quantity == 100

    await bus.stop()
    with contextlib.suppress(asyncio.CancelledError):
        await run


@pytest.mark.asyncio
async def test_realized_pnl_after_round_trip(session_factory) -> None:
    bus = InMemoryBus()
    await bus.start()
    store = InMemoryPositionStore()
    svc = ProjectorService(bus, store, session_factory)
    run = asyncio.ensure_future(svc.run())
    await asyncio.sleep(0)

    await bus.publish(topics.FILLS, _fill(Side.BUY, 100, "150"))
    await asyncio.sleep(0.02)
    await bus.publish(topics.FILLS, _fill(Side.SELL, 100, "160"))
    await asyncio.sleep(0.05)

    pos = await store.get("AAPL")
    assert pos.quantity == 0
    assert pos.realized_pnl == Decimal("1000")  # (160-150)*100
    assert svc.fills_processed == 2
    await bus.stop()
    with contextlib.suppress(asyncio.CancelledError):
        await run


@pytest.mark.asyncio
async def test_mark_updates_unrealized(session_factory) -> None:
    bus = InMemoryBus()
    await bus.start()
    store = InMemoryPositionStore()
    svc = ProjectorService(bus, store, session_factory)
    run = asyncio.ensure_future(svc.run())
    await asyncio.sleep(0)

    await bus.publish(topics.FILLS, _fill(Side.BUY, 100, "150"))
    await asyncio.sleep(0.02)
    c = Decimal("155")
    await bus.publish(
        topics.MARKET_BARS, Bar(symbol="AAPL", open=c, high=c, low=c, close=c, volume=1)
    )
    await asyncio.sleep(0.05)

    pos = await store.get("AAPL")
    assert pos.unrealized_pnl == Decimal("500")  # (155-150)*100
    await bus.stop()
    with contextlib.suppress(asyncio.CancelledError):
        await run
