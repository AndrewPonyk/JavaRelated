"""Tests for the strategy-engine run loop over the in-memory bus."""

from __future__ import annotations

import asyncio
import contextlib
from decimal import Decimal

import pytest
from strategy_engine.engine import StrategyEngine
from strategy_engine.strategies.base import Strategy
from strategy_engine.strategies.momentum import EmaCrossoverStrategy

from trading_common.messaging import InMemoryBus, topics
from trading_common.models.market import Bar, Side, Signal


def _bar(symbol: str, close: float) -> Bar:
    c = Decimal(str(close))
    return Bar(symbol=symbol, open=c, high=c, low=c, close=c, volume=1000)


class _TagStrategy(Strategy):
    warmup_bars = 0

    def evaluate(self, bar: Bar, history: list[Bar]) -> list[Signal]:
        return [Signal(strategy_id=self.strategy_id, symbol=bar.symbol, side=Side.BUY, quantity=1)]


async def _collect_signals(bus: InMemoryBus, n: int, timeout: float = 2.0) -> list[Signal]:
    sub = bus.subscribe(topics.SIGNALS, Signal, group="test")
    out: list[Signal] = []

    async def reader() -> None:
        async for sig in sub:
            out.append(sig)
            if len(out) >= n:
                return

    with contextlib.suppress(asyncio.TimeoutError):
        await asyncio.wait_for(reader(), timeout=timeout)
    return out


@pytest.mark.asyncio
async def test_engine_publishes_signal_for_subscribed_symbol() -> None:
    bus = InMemoryBus()
    await bus.start()
    engine = StrategyEngine(bus, [_TagStrategy("s1", ["AAPL"])])
    run = asyncio.ensure_future(engine.run())

    collector = asyncio.ensure_future(_collect_signals(bus, 1))
    await asyncio.sleep(0)  # let subscriptions register
    await bus.publish(topics.MARKET_BARS, _bar("AAPL", 100))
    signals = await collector

    assert len(signals) == 1
    assert signals[0].symbol == "AAPL"
    await bus.stop()
    await run


@pytest.mark.asyncio
async def test_engine_ignores_unsubscribed_symbol() -> None:
    bus = InMemoryBus()
    await bus.start()
    engine = StrategyEngine(bus, [_TagStrategy("s1", ["AAPL"])])
    run = asyncio.ensure_future(engine.run())

    collector = asyncio.ensure_future(_collect_signals(bus, 1, timeout=0.3))
    await asyncio.sleep(0)
    await bus.publish(topics.MARKET_BARS, _bar("TSLA", 100))  # not subscribed
    signals = await collector

    assert signals == []
    await bus.stop()
    await run


@pytest.mark.asyncio
async def test_strategy_exception_does_not_crash_engine() -> None:
    class Boom(Strategy):
        warmup_bars = 0

        def evaluate(self, bar: Bar, history: list[Bar]) -> list[Signal]:
            raise RuntimeError("boom")

    bus = InMemoryBus()
    await bus.start()
    engine = StrategyEngine(bus, [Boom("boom", ["AAPL"])])
    run = asyncio.ensure_future(engine.run())
    await asyncio.sleep(0)
    await bus.publish(topics.MARKET_BARS, _bar("AAPL", 100))  # must not raise
    await asyncio.sleep(0.05)
    await bus.stop()
    await run  # engine exited cleanly


def test_ema_crossover_warmup_blocks_early_signals() -> None:
    strat = EmaCrossoverStrategy("ema", ["AAPL"], fast=3, slow=5)
    signals: list[Signal] = []
    for price in (10, 11, 12):
        signals += strat.on_bar(_bar("AAPL", price))
    assert signals == []


def test_ema_crossover_emits_buy_on_upward_cross() -> None:
    # rsi_overbought=101 disables the RSI filter so we isolate the EMA cross.
    strat = EmaCrossoverStrategy("ema", ["AAPL"], fast=3, slow=5, rsi_period=2, rsi_overbought=101)
    prices = [20, 19, 18, 17, 16, 15, 16, 18, 21, 25, 30, 34, 38]
    emitted: list[Signal] = []
    for p in prices:
        emitted += strat.on_bar(_bar("AAPL", p))
    assert any(s.side is Side.BUY for s in emitted), "fast EMA should cross above slow EMA"


def test_ema_crossover_rsi_filter_blocks_overbought_buy() -> None:
    # Same rally but with a tight overbought threshold: the buy is suppressed.
    strat = EmaCrossoverStrategy("ema", ["AAPL"], fast=3, slow=5, rsi_period=2, rsi_overbought=50)
    emitted: list[Signal] = []
    for p in [20, 19, 18, 17, 16, 15, 16, 18, 21, 25, 30, 34, 38]:
        emitted += strat.on_bar(_bar("AAPL", p))
    assert all(s.side is not Side.BUY for s in emitted)
