"""Async strategy-engine host.

Consumes bars from ``market.bars``, drives the loaded strategies, and publishes
:class:`Signal` events to ``signals`` for the risk engine. This service only emits
*intent*; orders leave the system from the execution engine (TECH-NOTES §3.6).
"""

from __future__ import annotations

import asyncio
import contextlib
import signal as os_signal
from collections.abc import Sequence

from strategy_engine.strategies.base import Strategy
from trading_common.messaging import MessageBus, make_bus, topics
from trading_common.models.market import Bar
from trading_common.utils import configure_logging, get_logger, load_settings

log = get_logger(component="strategy-engine")


class StrategyEngine:
    """Owns the run loop: bar in -> strategies -> signals out."""

    def __init__(self, bus: MessageBus, strategies: Sequence[Strategy]) -> None:
        self._bus = bus
        self._strategies = list(strategies)
        self._by_symbol: dict[str, list[Strategy]] = {}
        for strat in self._strategies:
            for sym in strat.symbols:
                self._by_symbol.setdefault(sym, []).append(strat)
        self._signals_emitted = 0

    async def run(self) -> None:
        sub = self._bus.subscribe(topics.MARKET_BARS, Bar, group="strategy-engine")
        log.info("engine.started", strategies=[s.strategy_id for s in self._strategies])
        async for bar in sub:
            await self._dispatch(bar)
        log.info("engine.stopped", signals_emitted=self._signals_emitted)

    async def _dispatch(self, bar: Bar) -> None:
        for strat in self._by_symbol.get(bar.symbol, ()):
            try:
                for sig in strat.on_bar(bar):
                    await self._bus.publish(topics.SIGNALS, sig, key=sig.strategy_id)
                    self._signals_emitted += 1
                    log.info(
                        "signal.published",
                        strategy_id=sig.strategy_id,
                        symbol=sig.symbol,
                        side=sig.side.value,
                        qty=sig.quantity,
                        correlation_id=str(sig.correlation_id),
                    )
            except Exception:  # one bad strategy must not crash the host
                log.exception("strategy.error", strategy_id=strat.strategy_id, symbol=bar.symbol)


async def main() -> None:  # pragma: no cover - process entrypoint
    settings = load_settings("strategy-engine")
    configure_logging(settings.service_name, settings.env, settings.log_level)

    bus = make_bus(settings.kafka_bootstrap_servers, group_id="strategy-engine")
    await bus.start()

    from strategy_engine.registry import load_strategies

    strategies = await load_strategies(settings.database_url)
    engine = StrategyEngine(bus, strategies)

    loop = asyncio.get_running_loop()
    stop = asyncio.Event()
    for sig in (os_signal.SIGINT, os_signal.SIGTERM):
        with contextlib.suppress(NotImplementedError):
            loop.add_signal_handler(sig, stop.set)

    run_task = asyncio.ensure_future(engine.run())
    await stop.wait()
    await bus.stop()
    await run_task


if __name__ == "__main__":  # pragma: no cover
    asyncio.run(main())
