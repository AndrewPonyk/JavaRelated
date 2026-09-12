"""Persistence-layer tests against a real (in-memory SQLite) database."""

from __future__ import annotations

from decimal import Decimal
from uuid import uuid4

import pytest

from trading_common.db import (
    FillRow,
    OrderRow,
    PositionRepository,
    StrategyRepository,
    StrategyRow,
    session_scope,
)


@pytest.mark.asyncio
async def test_strategy_crud_roundtrip(session_factory) -> None:
    async with session_scope(session_factory) as session:
        repo = StrategyRepository(session)
        row = StrategyRow(
            name="EMA Crossover",
            klass="strategy_engine.strategies.momentum.EmaCrossoverStrategy",
            symbols=["AAPL", "MSFT"],
            params={"fast": 12, "slow": 26},
            state="DRAFT",
            max_position_qty=1000,
            max_order_notional=Decimal("250000"),
        )
        await repo.add(row)
        new_id = row.id

    async with session_scope(session_factory) as session:
        repo = StrategyRepository(session)
        fetched = await repo.get(new_id)
        assert fetched is not None
        assert fetched.symbols == ["AAPL", "MSFT"]
        assert fetched.params["fast"] == 12
        assert fetched.max_order_notional == Decimal("250000")


@pytest.mark.asyncio
async def test_get_by_name_and_list_active(session_factory) -> None:
    async with session_scope(session_factory) as session:
        repo = StrategyRepository(session)
        await repo.add(
            StrategyRow(
                name="live-one",
                klass="x",
                symbols=["AAPL"],
                params={},
                state="LIVE",
                max_position_qty=10,
                max_order_notional=Decimal("1"),
            )
        )
        await repo.add(
            StrategyRow(
                name="draft-one",
                klass="x",
                symbols=["AAPL"],
                params={},
                state="DRAFT",
                max_position_qty=10,
                max_order_notional=Decimal("1"),
            )
        )

    async with session_scope(session_factory) as session:
        repo = StrategyRepository(session)
        assert (await repo.get_by_name("live-one")) is not None
        active = await repo.list_active()
        assert [s.name for s in active] == ["live-one"]


@pytest.mark.asyncio
async def test_position_upsert(session_factory) -> None:
    async with session_scope(session_factory) as session:
        repo = PositionRepository(session)
        await repo.upsert("AAPL", 100, Decimal("150.00"), Decimal("0"))
        await repo.upsert("AAPL", 150, Decimal("155.00"), Decimal("25"))

    async with session_scope(session_factory) as session:
        repo = PositionRepository(session)
        pos = await repo.get("AAPL")
        assert pos is not None
        assert pos.quantity == 150
        assert pos.avg_price == Decimal("155.00")
        assert pos.realized_pnl == Decimal("25")


@pytest.mark.asyncio
async def test_order_and_fill_relationship(session_factory) -> None:
    async with session_scope(session_factory) as session:
        strat = StrategyRow(
            name="s",
            klass="x",
            symbols=["AAPL"],
            params={},
            state="PAPER",
            max_position_qty=10,
            max_order_notional=Decimal("1"),
        )
        session.add(strat)
        await session.flush()
        order = OrderRow(
            client_order_id="c-1",
            strategy_id=strat.id,
            symbol="AAPL",
            side="BUY",
            order_type="MARKET",
            quantity=100,
            status="FILLED",
            correlation_id=uuid4(),
        )
        session.add(order)
        await session.flush()
        session.add(
            FillRow(
                order_id=order.id, symbol="AAPL", side="BUY", quantity=100, price=Decimal("150.0")
            )
        )

    async with session_scope(session_factory) as session:
        from trading_common.db import FillRepository, OrderRepository

        assert (await OrderRepository(session).get_by_client_id("c-1")) is not None
        fills = await FillRepository(session).list()
        assert len(fills) == 1 and fills[0].quantity == 100
