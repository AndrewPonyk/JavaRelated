"""Paper matching engine — simulated fills for paper trading and backtests.

This is the "simulated matching engine" the architecture routes to in paper mode
instead of a live broker (PROJECT-PLAN Phase 2). It models commission and slippage
so paper/backtest results are not naively optimistic (TECH-NOTES §3.6).

Live trading uses the C++ ``execution-engine`` over FIX instead; this stays in the
Python control plane for simulation.
"""

from __future__ import annotations

from decimal import Decimal

from trading_common.models.market import Fill, Order, OrderType, Side


class PaperMatchingEngine:
    def __init__(
        self,
        *,
        slippage_bps: Decimal = Decimal("1"),  # 1 bp adverse slippage
        commission_per_share: Decimal = Decimal("0.005"),
    ) -> None:
        self._slippage_bps = slippage_bps
        self._commission_per_share = commission_per_share

    def _apply_slippage(self, price: Decimal, side: Side) -> Decimal:
        # Buyers pay up, sellers receive less (adverse selection).
        factor = self._slippage_bps / Decimal("10000")
        adj = price * factor
        return price + adj if side is Side.BUY else price - adj

    def execute(self, order: Order, reference_price: Decimal) -> Fill:
        """Match an order against ``reference_price``, returning a full fill.

        For LIMIT orders, the order only fills if the reference price is at or
        through the limit; otherwise a zero-quantity (unfilled) fill is returned.
        """
        if order.order_type is OrderType.LIMIT and order.limit_price is not None:
            marketable = (
                reference_price <= order.limit_price
                if order.side is Side.BUY
                else reference_price >= order.limit_price
            )
            if not marketable:
                return Fill(
                    correlation_id=order.correlation_id,
                    order_id=order.order_id,
                    symbol=order.symbol,
                    side=order.side,
                    quantity=order.quantity,
                    price=order.limit_price,
                    commission=Decimal("0"),
                    is_partial=True,  # treated as not-yet-filled
                )
            fill_price = (
                min(reference_price, order.limit_price)
                if order.side is Side.BUY
                else max(reference_price, order.limit_price)
            )
        else:
            fill_price = self._apply_slippage(reference_price, order.side)

        commission = self._commission_per_share * Decimal(order.quantity)
        return Fill(
            correlation_id=order.correlation_id,
            order_id=order.order_id,
            symbol=order.symbol,
            side=order.side,
            quantity=order.quantity,
            price=fill_price.quantize(Decimal("0.0001")),
            commission=commission.quantize(Decimal("0.0001")),
            is_partial=False,
        )
