"""Tests for the SimpleBacktester and metrics."""

from __future__ import annotations

from decimal import Decimal

import pytest

from trading_common.models.market import Bar, Side, Signal

from backtesting.metrics import compute_metrics
from backtesting.simple import SimpleBacktester
from strategy_engine.strategies.base import Strategy
from strategy_engine.strategies.momentum import EmaCrossoverStrategy


def _bars(prices: list[float]) -> list[Bar]:
    out = []
    for p in prices:
        c = Decimal(str(p))
        out.append(Bar(symbol="AAPL", open=c, high=c, low=c, close=c, volume=1000))
    return out


class BuyAndHold(Strategy):
    warmup_bars = 0

    def __init__(self, strategy_id: str, symbols: list[str]) -> None:
        super().__init__(strategy_id, symbols)
        self._bought = False

    def evaluate(self, bar: Bar, history: list[Bar]) -> list[Signal]:
        if not self._bought:
            self._bought = True
            return [Signal(strategy_id=self.strategy_id, symbol=bar.symbol, side=Side.BUY, quantity=100)]
        return []


def test_metrics_basic() -> None:
    result = compute_metrics([100.0, 110.0, 121.0], n_trades=1)
    assert result.total_return == pytest.approx(0.21)
    assert result.n_trades == 1
    assert result.max_drawdown == 0.0  # monotonic up => no drawdown


def test_metrics_drawdown() -> None:
    result = compute_metrics([100.0, 120.0, 90.0, 95.0], n_trades=0)
    assert result.max_drawdown < 0  # there was a peak-to-trough decline


def test_buy_and_hold_profits_in_uptrend() -> None:
    bt = SimpleBacktester(starting_cash=Decimal("1000000"))
    result = bt.run(BuyAndHold("bh", ["AAPL"]), _bars(list(range(100, 140))))
    assert result.n_trades == 1
    assert result.total_return > 0  # price rose after we bought
    assert result.n_bars == 40


def test_backtest_runs_with_real_strategy() -> None:
    bt = SimpleBacktester()
    prices = [20, 19, 18, 17, 16, 15, 16, 18, 21, 25, 30, 34, 38, 36, 33, 30]
    result = bt.run(
        EmaCrossoverStrategy("ema", ["AAPL"], fast=3, slow=5, rsi_overbought=101),
        _bars(prices),
    )
    assert result.n_bars == len(prices)
    assert isinstance(result.sharpe, float)
