"""Market-data ingestion: external feed -> normalize -> bus.

Publishes normalized :class:`Tick` to ``market.ticks`` and aggregated
:class:`Bar` to ``market.bars`` (the unit strategies consume).
"""

from __future__ import annotations

import abc
import math
from collections.abc import AsyncIterator
from decimal import Decimal

from trading_common.messaging import MessageBus, topics
from trading_common.models.market import Bar, Tick
from trading_common.utils import get_logger

log = get_logger(component="market-data")


class FeedAdapter(abc.ABC):
    """Vendor adapter contract — one per data provider / FIX market-data session."""

    @abc.abstractmethod
    def stream(self) -> AsyncIterator[Tick]:
        """Yield normalized ticks from the vendor feed."""
        raise NotImplementedError


class SimulatedFeed(FeedAdapter):
    """Deterministic synthetic feed for local dev and tests (Milestone M1)."""

    def __init__(
        self, symbol: str, start: float = 100.0, steps: int = 1000, amplitude: float = 2.0
    ) -> None:
        self._symbol = symbol
        self._start = start
        self._steps = steps
        self._amplitude = amplitude

    async def stream(self) -> AsyncIterator[Tick]:
        for i in range(self._steps):
            mid = self._start + math.sin(i / 20) * self._amplitude + (i * 0.01)
            yield Tick(
                symbol=self._symbol,
                bid=Decimal(f"{mid - 0.01:.2f}"),
                ask=Decimal(f"{mid + 0.01:.2f}"),
                last=Decimal(f"{mid:.2f}"),
                volume=100,
            )


class BarAggregator:
    """Folds a stream of ticks into fixed-size OHLCV bars (count-based)."""

    def __init__(self, ticks_per_bar: int = 5, interval: str = "1m") -> None:
        self._n = ticks_per_bar
        self._interval = interval
        self._buf: dict[str, list[Tick]] = {}

    def add(self, tick: Tick) -> Bar | None:
        buf = self._buf.setdefault(tick.symbol, [])
        buf.append(tick)
        if len(buf) < self._n:
            return None
        bar = self._make_bar(tick.symbol, buf)
        self._buf[tick.symbol] = []
        return bar

    def _make_bar(self, symbol: str, ticks: list[Tick]) -> Bar:
        prices = [t.last for t in ticks]
        return Bar(
            correlation_id=ticks[-1].correlation_id,
            symbol=symbol,
            open=prices[0],
            high=max(prices),
            low=min(prices),
            close=prices[-1],
            volume=sum(t.volume for t in ticks),
            interval=self._interval,
        )


class MarketDataPublisher:
    """Pumps a FeedAdapter into the bus (ticks + aggregated bars)."""

    def __init__(
        self, bus: MessageBus, adapter: FeedAdapter, aggregator: BarAggregator | None = None
    ) -> None:
        self._bus = bus
        self._adapter = adapter
        self._aggregator = aggregator or BarAggregator()
        self.ticks_published = 0
        self.bars_published = 0

    async def run(self, max_ticks: int | None = None) -> None:
        async for tick in self._adapter.stream():
            await self._bus.publish(topics.MARKET_TICKS, tick, key=tick.symbol)
            self.ticks_published += 1
            bar = self._aggregator.add(tick)
            if bar is not None:
                await self._bus.publish(topics.MARKET_BARS, bar, key=bar.symbol)
                self.bars_published += 1
            if max_ticks is not None and self.ticks_published >= max_ticks:
                return
