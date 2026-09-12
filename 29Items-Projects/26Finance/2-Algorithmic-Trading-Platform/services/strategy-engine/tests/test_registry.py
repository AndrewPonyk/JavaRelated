"""Tests for the strategy registry loader."""

from __future__ import annotations

from decimal import Decimal

import pytest
from strategy_engine.registry import instantiate, load_strategies
from strategy_engine.strategies.momentum import EmaCrossoverStrategy

from trading_common.db import (
    StrategyRow,
    create_all,
    make_engine,
    make_session_factory,
    session_scope,
)


def test_instantiate_builds_strategy() -> None:
    strat = instantiate(
        "strategy_engine.strategies.momentum.EmaCrossoverStrategy",
        "id-1",
        ["AAPL", "MSFT"],
        {"fast": 5, "slow": 10},
    )
    assert isinstance(strat, EmaCrossoverStrategy)
    assert strat.symbols == ["AAPL", "MSFT"]
    assert strat.fast == 5


def test_instantiate_rejects_non_strategy() -> None:
    with pytest.raises(TypeError):
        instantiate("decimal.Decimal", "id", ["AAPL"], {})


def test_instantiate_rejects_bad_path() -> None:
    with pytest.raises(ValueError):
        instantiate("NotADottedPath", "id", ["AAPL"], {})


@pytest.mark.asyncio
async def test_load_strategies_from_db(tmp_path) -> None:
    url = f"sqlite:///{(tmp_path / 'reg.db').as_posix()}"
    engine = make_engine(url)
    await create_all(engine)
    factory = make_session_factory(engine)
    async with session_scope(factory) as session:
        session.add(
            StrategyRow(
                name="paper-ema",
                klass="strategy_engine.strategies.momentum.EmaCrossoverStrategy",
                symbols=["AAPL"],
                params={"fast": 3, "slow": 5},
                state="PAPER",
                max_position_qty=100,
                max_order_notional=Decimal("1000"),
            )
        )
        session.add(
            StrategyRow(
                name="draft-ignored",
                klass="strategy_engine.strategies.momentum.EmaCrossoverStrategy",
                symbols=["MSFT"],
                params={},
                state="DRAFT",
                max_position_qty=100,
                max_order_notional=Decimal("1000"),
            )
        )
    await engine.dispose()

    strategies = await load_strategies(url)
    assert len(strategies) == 1  # only PAPER/LIVE loaded
    assert strategies[0].symbols == ["AAPL"]
