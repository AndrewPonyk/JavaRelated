"""Tests for average-cost PnL accounting and the projector."""

from __future__ import annotations

from decimal import Decimal

import pytest

from trading_common.models.market import Fill, Position, Side
from trading_common.portfolio import (
    InMemoryPositionStore,
    Projector,
    apply_fill,
)


def _fill(side: Side, qty: int, price: str, commission: str = "0") -> Fill:
    from uuid import uuid4

    return Fill(
        order_id=uuid4(),
        symbol="AAPL",
        side=side,
        quantity=qty,
        price=Decimal(price),
        commission=Decimal(commission),
    )


def test_open_long_sets_avg_price() -> None:
    pos = apply_fill(Position(symbol="AAPL"), _fill(Side.BUY, 100, "150"))
    assert pos.quantity == 100
    assert pos.avg_price == Decimal("150")
    assert pos.realized_pnl == Decimal("0")


def test_increase_long_rolls_average() -> None:
    pos = Position(symbol="AAPL", quantity=100, avg_price=Decimal("150"))
    pos = apply_fill(pos, _fill(Side.BUY, 100, "170"))
    assert pos.quantity == 200
    assert pos.avg_price == Decimal("160")  # (150*100 + 170*100)/200


def test_close_long_realizes_pnl() -> None:
    pos = Position(symbol="AAPL", quantity=100, avg_price=Decimal("150"))
    pos = apply_fill(pos, _fill(Side.SELL, 100, "160"))
    assert pos.quantity == 0
    assert pos.realized_pnl == Decimal("1000")  # (160-150)*100


def test_partial_close_keeps_average() -> None:
    pos = Position(symbol="AAPL", quantity=100, avg_price=Decimal("150"))
    pos = apply_fill(pos, _fill(Side.SELL, 40, "160"))
    assert pos.quantity == 60
    assert pos.avg_price == Decimal("150")
    assert pos.realized_pnl == Decimal("400")  # (160-150)*40


def test_flip_long_to_short() -> None:
    pos = Position(symbol="AAPL", quantity=100, avg_price=Decimal("150"))
    pos = apply_fill(pos, _fill(Side.SELL, 150, "160"))
    assert pos.quantity == -50
    assert pos.realized_pnl == Decimal("1000")  # closed 100 @ +10
    assert pos.avg_price == Decimal("160")  # new short basis


def test_short_then_cover_profit() -> None:
    pos = apply_fill(Position(symbol="AAPL"), _fill(Side.SELL, 100, "150"))
    assert pos.quantity == -100 and pos.avg_price == Decimal("150")
    pos = apply_fill(pos, _fill(Side.BUY, 100, "140"))
    assert pos.quantity == 0
    assert pos.realized_pnl == Decimal("1000")  # (150-140)*100


def test_commission_reduces_realized() -> None:
    pos = apply_fill(Position(symbol="AAPL"), _fill(Side.BUY, 100, "150", commission="5"))
    assert pos.realized_pnl == Decimal("-5")


def test_unrealized_uses_mark() -> None:
    pos = apply_fill(
        Position(symbol="AAPL"),
        _fill(Side.BUY, 100, "150"),
    )
    # mark defaults to fill price => unrealized 0
    assert pos.unrealized_pnl == Decimal("0")


@pytest.mark.asyncio
async def test_projector_folds_fills_and_marks() -> None:
    store = InMemoryPositionStore()
    proj = Projector(store)
    await proj.on_fill(_fill(Side.BUY, 100, "150"))
    await proj.on_mark("AAPL", Decimal("155"))
    pos = await store.get("AAPL")
    assert pos.quantity == 100
    assert pos.unrealized_pnl == Decimal("500")  # (155-150)*100
