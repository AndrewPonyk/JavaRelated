"""Paper-trading composition root.

Wires the whole control plane into ONE process over the in-memory bus:

    market-data ──ticks/bars──▶ strategy-engine ──signals──▶ risk-engine
        │                                                        │
        └──────────────── bars (marks) ──────────┐          orders
                                                 ▼              ▼
                              pnl-projector ◀──fills── paper-broker

This is the runnable local "paper trading" app and the substrate for the
end-to-end integration test. In production these are separate services over
Kafka; the wiring is identical because every component talks only to the bus.
"""

from __future__ import annotations

import asyncio
from decimal import Decimal

from market_data.feed_handler import BarAggregator, MarketDataPublisher, SimulatedFeed
from pnl_projector.projector_service import ProjectorService
from risk_engine.limits import RiskLimits
from risk_engine.service import RiskService
from sqlalchemy.ext.asyncio import async_sessionmaker
from strategy_engine.engine import StrategyEngine
from strategy_engine.strategies.base import Strategy
from strategy_engine.strategies.momentum import EmaCrossoverStrategy

from trading_common.messaging import InMemoryBus
from trading_common.portfolio import InMemoryPositionStore, PositionStore
from trading_common.sim import PaperBroker
from trading_common.utils import get_logger

log = get_logger(component="orchestrator")


class PaperTradingApp:
    """Single-process paper-trading runtime."""

    def __init__(
        self,
        *,
        strategies: list[Strategy] | None = None,
        limits: RiskLimits | None = None,
        session_factory: async_sessionmaker | None = None,
        position_store: PositionStore | None = None,
        ticks_per_bar: int = 5,
    ) -> None:
        self.bus = InMemoryBus()
        self.store = position_store or InMemoryPositionStore()
        self.strategies = strategies or [EmaCrossoverStrategy("ema-demo", ["AAPL"], fast=3, slow=8)]
        self.limits = limits or RiskLimits(
            max_position_qty=100_000,
            max_order_notional=Decimal("10000000"),
            max_daily_loss=Decimal("1000000"),
        )
        self.ticks_per_bar = ticks_per_bar

        self.engine = StrategyEngine(self.bus, self.strategies)
        self.risk = RiskService(self.bus, self.limits, self.store)
        self.broker = PaperBroker(self.bus)
        self.projector = ProjectorService(self.bus, self.store, session_factory)
        symbols = sorted({s for strat in self.strategies for s in strat.symbols})
        self._feeds = [SimulatedFeed(sym, steps=10_000) for sym in symbols]

    async def run(self, *, max_ticks: int = 200) -> None:
        """Run consumers, drive the feed for ``max_ticks``, then drain and stop."""
        await self.bus.start()
        consumers = [
            asyncio.ensure_future(self.engine.run()),
            asyncio.ensure_future(self.risk.run()),
            asyncio.ensure_future(self.broker.run()),
            asyncio.ensure_future(self.projector.run()),
        ]
        await asyncio.sleep(0)  # let every consumer subscribe before data flows

        publishers = [
            MarketDataPublisher(self.bus, feed, BarAggregator(ticks_per_bar=self.ticks_per_bar))
            for feed in self._feeds
        ]
        await asyncio.gather(*(p.run(max_ticks=max_ticks) for p in publishers))

        await asyncio.sleep(0.1)  # let the last events drain through the pipeline
        await self.bus.stop()
        await asyncio.gather(*consumers, return_exceptions=True)
        log.info(
            "paper.session_complete",
            signals=self.engine._signals_emitted,
            approved=self.risk.approved,
            rejected=self.risk.rejected,
            fills=self.broker.fills_published,
        )
