"""Strategy registry — instantiate live strategies from the DB registry.

Reads PAPER/LIVE strategy rows and constructs the concrete :class:`Strategy`
subclass named by ``row.klass``, passing ``row.params`` as keyword arguments.
"""

from __future__ import annotations

import importlib

from strategy_engine.strategies.base import Strategy
from trading_common.db import StrategyRepository, make_engine, make_session_factory, session_scope
from trading_common.utils import get_logger

log = get_logger(component="strategy-registry")


def instantiate(klass_path: str, strategy_id: str, symbols: list[str], params: dict) -> Strategy:
    """Build a Strategy from a fully-qualified class path and params."""
    module_path, _, class_name = klass_path.rpartition(".")
    if not module_path:
        raise ValueError(f"invalid strategy class path: {klass_path!r}")
    module = importlib.import_module(module_path)
    cls = getattr(module, class_name)
    if not (isinstance(cls, type) and issubclass(cls, Strategy)):
        raise TypeError(f"{klass_path} is not a Strategy subclass")
    return cls(strategy_id=strategy_id, symbols=symbols, **params)


async def load_strategies(database_url: str) -> list[Strategy]:
    """Load all PAPER/LIVE strategies from the database."""
    engine = make_engine(database_url)
    factory = make_session_factory(engine)
    strategies: list[Strategy] = []
    try:
        async with session_scope(factory) as session:
            for row in await StrategyRepository(session).list_active():
                try:
                    strategies.append(
                        instantiate(row.klass, str(row.id), list(row.symbols), dict(row.params))
                    )
                except Exception:
                    log.exception("registry.load_failed", strategy=row.name, klass=row.klass)
    finally:
        await engine.dispose()
    log.info("registry.loaded", count=len(strategies))
    return strategies
