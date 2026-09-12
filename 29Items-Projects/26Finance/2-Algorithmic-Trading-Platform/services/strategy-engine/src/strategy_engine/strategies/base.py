"""The Strategy contract — THE backtest/live parity boundary.

The exact same subclass instance is driven by:
  * ``backtesting`` (Zipline harness) over historical bars, and
  * ``strategy-engine`` over live Kafka bars.

Therefore a Strategy must be *pure* with respect to its inputs: given the same
sequence of bars it must emit the same sequence of signals. No wall-clock reads,
no network, no peeking at future bars (look-ahead bias — TECH-NOTES §3.6).
"""

from __future__ import annotations

import abc
from collections import deque
from collections.abc import Iterable

from trading_common.models.market import Bar, Signal


class Strategy(abc.ABC):
    """Base class for all trading strategies."""

    #: how many trailing bars the strategy needs before it can act
    warmup_bars: int = 0

    def __init__(self, strategy_id: str, symbols: Iterable[str]) -> None:
        self.strategy_id = strategy_id
        self.symbols = list(symbols)
        # bounded per-symbol history; deque avoids unbounded memory growth
        self._history: dict[str, deque[Bar]] = {
            s: deque(maxlen=max(self.warmup_bars * 4, 256)) for s in self.symbols
        }

    def ready(self, symbol: str) -> bool:
        """True once enough bars have accumulated to compute indicators."""
        return len(self._history[symbol]) >= self.warmup_bars

    def on_bar(self, bar: Bar) -> list[Signal]:
        """Framework entry point. Records history, then delegates to ``evaluate``."""
        hist = self._history.setdefault(bar.symbol, deque(maxlen=256))
        hist.append(bar)
        if not self.ready(bar.symbol):
            return []
        return self.evaluate(bar, list(hist))

    @abc.abstractmethod
    def evaluate(self, bar: Bar, history: list[Bar]) -> list[Signal]:
        """Pure decision function. ``history`` ends with ``bar`` (inclusive).

        Return zero or more :class:`Signal` objects. Must not access any data
        beyond ``history`` (no look-ahead, no side effects).
        """
        raise NotImplementedError
