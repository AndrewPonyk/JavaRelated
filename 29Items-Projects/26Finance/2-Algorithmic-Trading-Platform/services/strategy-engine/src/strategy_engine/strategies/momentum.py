"""Example strategy: dual-EMA crossover with an RSI filter.

Demonstrates the parity-safe pattern: all indicator math comes from
``trading_common.indicators`` (the single shared code path), and the decision is
a pure function of ``history``.
"""

from __future__ import annotations

import numpy as np

from strategy_engine.strategies.base import Strategy
from trading_common.indicators import ema, rsi
from trading_common.models.market import Bar, Side, Signal


class EmaCrossoverStrategy(Strategy):
    """Go long when fast EMA crosses above slow EMA and RSI is not overbought."""

    def __init__(
        self,
        strategy_id: str,
        symbols: list[str],
        fast: int = 12,
        slow: int = 26,
        rsi_period: int = 14,
        rsi_overbought: float = 70.0,
        quantity: int = 100,
    ) -> None:
        self.fast = fast
        self.slow = slow
        self.rsi_period = rsi_period
        self.rsi_overbought = rsi_overbought
        self.quantity = quantity
        self.warmup_bars = slow + 1  # need enough bars for the slow EMA
        super().__init__(strategy_id, symbols)

    def evaluate(self, bar: Bar, history: list[Bar]) -> list[Signal]:
        closes = np.array([float(b.close) for b in history], dtype=np.float64)

        fast_ema = ema(closes, self.fast)
        slow_ema = ema(closes, self.slow)
        rsi_vals = rsi(closes, self.rsi_period)

        # Need the last two points of both EMAs to detect a crossover.
        if np.isnan(fast_ema[-2:]).any() or np.isnan(slow_ema[-2:]).any():
            return []

        crossed_up = fast_ema[-2] <= slow_ema[-2] and fast_ema[-1] > slow_ema[-1]
        crossed_down = fast_ema[-2] >= slow_ema[-2] and fast_ema[-1] < slow_ema[-1]
        not_overbought = float(rsi_vals[-1]) < self.rsi_overbought

        if crossed_up and not_overbought:
            return [
                Signal(
                    correlation_id=bar.correlation_id,  # propagate trace id from the bar
                    strategy_id=self.strategy_id,
                    symbol=bar.symbol,
                    side=Side.BUY,
                    quantity=self.quantity,
                    confidence=min(1.0, (self.rsi_overbought - float(rsi_vals[-1])) / 100.0),
                )
            ]
        if crossed_down:
            return [
                Signal(
                    correlation_id=bar.correlation_id,
                    strategy_id=self.strategy_id,
                    symbol=bar.symbol,
                    side=Side.SELL,
                    quantity=self.quantity,
                )
            ]
        return []
