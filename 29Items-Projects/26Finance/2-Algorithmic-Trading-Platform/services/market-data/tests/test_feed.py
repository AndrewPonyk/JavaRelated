"""Tests for the simulated feed, bar aggregator, and publisher."""

from __future__ import annotations

import pytest
from market_data.feed_handler import (
    BarAggregator,
    MarketDataPublisher,
    SimulatedFeed,
)

from trading_common.messaging import InMemoryBus, topics
from trading_common.models.market import Bar, Tick


@pytest.mark.asyncio
async def test_simulated_feed_yields_ticks() -> None:
    feed = SimulatedFeed("AAPL", steps=10)
    ticks = [t async for t in feed.stream()]
    assert len(ticks) == 10
    assert all(t.symbol == "AAPL" and t.ask > t.bid for t in ticks)


def test_bar_aggregator_emits_every_n_ticks() -> None:
    from decimal import Decimal

    agg = BarAggregator(ticks_per_bar=3)
    bars = []
    for px in (10, 12, 11, 9, 13, 14):
        c = Decimal(str(px))
        bar = agg.add(Tick(symbol="AAPL", bid=c, ask=c, last=c))
        if bar:
            bars.append(bar)
    assert len(bars) == 2
    assert bars[0].open == Decimal("10")
    assert bars[0].high == Decimal("12")
    assert bars[0].low == Decimal("10")
    assert bars[0].close == Decimal("11")


@pytest.mark.asyncio
async def test_publisher_emits_ticks_and_bars() -> None:
    bus = InMemoryBus()
    await bus.start()
    tick_sub = bus.subscribe(topics.MARKET_TICKS, Tick, group="t")
    bar_sub = bus.subscribe(topics.MARKET_BARS, Bar, group="b")

    pub = MarketDataPublisher(bus, SimulatedFeed("AAPL", steps=10), BarAggregator(ticks_per_bar=5))
    await pub.run(max_ticks=10)

    assert pub.ticks_published == 10
    assert pub.bars_published == 2

    ticks = [await anext(aiter(tick_sub)) for _ in range(10)]
    assert len(ticks) == 10
    bars = [await anext(aiter(bar_sub)) for _ in range(2)]
    assert len(bars) == 2 and all(b.symbol == "AAPL" for b in bars)
    await bus.stop()
