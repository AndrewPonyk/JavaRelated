"""Tests for the paper matching engine (slippage, commission, limit logic)."""

from __future__ import annotations

from decimal import Decimal
from uuid import uuid4

from trading_common.models.market import Order, OrderType, Side
from trading_common.sim import PaperMatchingEngine


def _order(
    side: Side, qty: int = 100, otype: OrderType = OrderType.MARKET, limit: str | None = None
) -> Order:
    return Order(
        client_order_id="c-1",
        order_id=uuid4(),
        strategy_id="s",
        symbol="AAPL",
        side=side,
        order_type=otype,
        quantity=qty,
        limit_price=Decimal(limit) if limit else None,
    )


def test_market_buy_pays_slippage_up() -> None:
    eng = PaperMatchingEngine(slippage_bps=Decimal("10"), commission_per_share=Decimal("0"))
    fill = eng.execute(_order(Side.BUY), Decimal("100"))
    assert fill.price > Decimal("100")  # buyer pays up
    assert not fill.is_partial


def test_market_sell_receives_slippage_down() -> None:
    eng = PaperMatchingEngine(slippage_bps=Decimal("10"), commission_per_share=Decimal("0"))
    fill = eng.execute(_order(Side.SELL), Decimal("100"))
    assert fill.price < Decimal("100")


def test_commission_scales_with_quantity() -> None:
    eng = PaperMatchingEngine(slippage_bps=Decimal("0"), commission_per_share=Decimal("0.01"))
    fill = eng.execute(_order(Side.BUY, qty=200), Decimal("50"))
    assert fill.commission == Decimal("2.0000")


def test_marketable_limit_fills() -> None:
    eng = PaperMatchingEngine(commission_per_share=Decimal("0"))
    fill = eng.execute(_order(Side.BUY, otype=OrderType.LIMIT, limit="101"), Decimal("100"))
    assert not fill.is_partial
    assert fill.price <= Decimal("101")


def test_nonmarketable_limit_does_not_fill() -> None:
    eng = PaperMatchingEngine()
    fill = eng.execute(_order(Side.BUY, otype=OrderType.LIMIT, limit="99"), Decimal("100"))
    assert fill.is_partial  # buy limit below market => not filled
