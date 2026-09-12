"""SimpleBacktester — an event-driven backtester with backtest/live parity.

Drives the SAME ``Strategy`` subclasses that run live in the strategy-engine over
a sequence of historical bars, simulating fills with the SAME
:class:`PaperMatchingEngine` used in paper trading, and folding them through the
SAME average-cost accounting (``apply_fill``). One signal/fill/PnL code path =>
backtest results faithfully reflect live behavior (ARCHITECTURE §2.3.2).

This is the default backtester. ``backtesting.runner`` wraps Zipline as a heavy
optional alternative.
"""

from __future__ import annotations

from decimal import Decimal

from trading_common.models.market import Bar, Position
from trading_common.portfolio import apply_fill
from trading_common.sim import PaperMatchingEngine

from backtesting.metrics import BacktestResult, compute_metrics
from strategy_engine.strategies.base import Strategy


class SimpleBacktester:
    def __init__(
        self,
        *,
        starting_cash: Decimal = Decimal("1000000"),
        matching_engine: PaperMatchingEngine | None = None,
    ) -> None:
        self._starting_cash = starting_cash
        self._engine = matching_engine or PaperMatchingEngine()

    def run(self, strategy: Strategy, bars: list[Bar]) -> BacktestResult:
        from risk_engine.service import make_order_from_signal  # reuse live order factory

        cash = self._starting_cash
        positions: dict[str, Position] = {}
        equity_curve: list[float] = []
        n_trades = 0

        for bar in bars:
            # 1) drive the strategy (same code as live)
            for signal in strategy.on_bar(bar):
                order = make_order_from_signal(signal)
                fill = self._engine.execute(order, bar.close)
                if fill.is_partial:  # non-marketable limit
                    continue
                pos = positions.get(fill.symbol, Position(symbol=fill.symbol))
                cash -= self._cash_delta(fill.side.value, fill.quantity, fill.price, fill.commission)
                positions[fill.symbol] = apply_fill(pos, fill, mark=bar.close)
                n_trades += 1

            # 2) mark-to-market equity at the bar close
            mtm = cash
            for pos in positions.values():
                if pos.quantity != 0:
                    mtm += pos.avg_price * Decimal(pos.quantity)  # cost basis held
                    mtm += (bar.close - pos.avg_price) * Decimal(pos.quantity)  # unrealized
            equity_curve.append(float(mtm))

        return compute_metrics(equity_curve, n_trades)

    @staticmethod
    def _cash_delta(side: str, qty: int, price: Decimal, commission: Decimal) -> Decimal:
        """Cash leaves on a buy, returns on a sell; commission always costs."""
        notional = price * Decimal(qty)
        return (notional if side == "BUY" else -notional) + commission
