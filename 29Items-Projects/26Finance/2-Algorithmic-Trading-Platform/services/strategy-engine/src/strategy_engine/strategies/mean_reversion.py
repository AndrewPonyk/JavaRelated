"""Mean-reversion strategy using Bollinger Bands.

Buys when price closes below the lower band (oversold) and sells when it closes
above the upper band (overbought). Pure function of ``history`` like every
Strategy (backtest/live parity).
"""

from __future__ import annotations

import numpy as np

from strategy_engine.strategies.base import Strategy
from trading_common.indicators import bollinger
from trading_common.models.market import Bar, Side, Signal


class BollingerReversionStrategy(Strategy):
    def __init__(
        self,
        strategy_id: str,
        symbols: list[str],
        period: int = 20,
        num_std: float = 2.0,
        quantity: int = 100,
    ) -> None:
        self.period = period
        self.num_std = num_std
        self.quantity = quantity
        self.warmup_bars = period + 1
        super().__init__(strategy_id, symbols)

    def evaluate(self, bar: Bar, history: list[Bar]) -> list[Signal]:
        closes = np.array([float(b.close) for b in history], dtype=np.float64)
        upper, _, lower = bollinger(closes, self.period, self.num_std)
        if np.isnan(upper[-1]) or np.isnan(lower[-1]):
            return []

        price = float(bar.close)
        if price < lower[-1]:
            return [self._signal(bar, Side.BUY)]
        if price > upper[-1]:
            return [self._signal(bar, Side.SELL)]
        return []

    def _signal(self, bar: Bar, side: Side) -> Signal:
        return Signal(
            correlation_id=bar.correlation_id,
            strategy_id=self.strategy_id,
            symbol=bar.symbol,
            side=side,
            quantity=self.quantity,
        )
