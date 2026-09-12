"""Direct unit tests for StrategyService (business logic, no HTTP layer).

These exercise the service against a real in-memory DB session, complementing the
end-to-end API tests in test_strategies.py.
"""

from __future__ import annotations

import pytest
from api_gateway.schemas.strategy import StrategyCreate, StrategyState, StrategyUpdate
from api_gateway.services.strategy_service import (
    DuplicateStrategyError,
    InvalidStateTransitionError,
    StrategyNotFoundError,
    StrategyService,
)

from trading_common.db import session_scope


def _create(name: str = "svc-strat") -> StrategyCreate:
    return StrategyCreate.model_validate(
        {
            "name": name,
            "class": "strategy_engine.strategies.momentum.EmaCrossoverStrategy",
            "symbols": ["aapl"],
            "params": {"fast": 12},
            "max_position_qty": 1000,
            "max_order_notional": 250000,
        }
    )


@pytest.mark.asyncio
async def test_create_and_get(session_factory) -> None:
    async with session_scope(session_factory) as session:
        svc = StrategyService(session)
        created = await svc.create(_create())
        assert created.state is StrategyState.DRAFT
        fetched = await svc.get(created.id)
        assert fetched.name == "svc-strat"
        assert fetched.symbols == ["AAPL"]


@pytest.mark.asyncio
async def test_create_duplicate_raises(session_factory) -> None:
    async with session_scope(session_factory) as session:
        svc = StrategyService(session)
        await svc.create(_create("dup"))
        with pytest.raises(DuplicateStrategyError):
            await svc.create(_create("dup"))


@pytest.mark.asyncio
async def test_get_missing_raises(session_factory) -> None:
    from uuid import uuid4

    async with session_scope(session_factory) as session:
        with pytest.raises(StrategyNotFoundError):
            await StrategyService(session).get(uuid4())


@pytest.mark.asyncio
async def test_full_lifecycle(session_factory) -> None:
    async with session_scope(session_factory) as session:
        svc = StrategyService(session)
        s = await svc.create(_create())
        for state in (StrategyState.BACKTESTING, StrategyState.PAPER, StrategyState.LIVE):
            updated = await svc.update(s.id, StrategyUpdate(state=state))
            assert updated.state is state


@pytest.mark.asyncio
async def test_illegal_transition_raises(session_factory) -> None:
    async with session_scope(session_factory) as session:
        svc = StrategyService(session)
        s = await svc.create(_create())
        with pytest.raises(InvalidStateTransitionError):
            await svc.update(s.id, StrategyUpdate(state=StrategyState.LIVE))  # DRAFT->LIVE


@pytest.mark.asyncio
async def test_update_fields_and_rename(session_factory) -> None:
    async with session_scope(session_factory) as session:
        svc = StrategyService(session)
        s = await svc.create(_create("orig"))
        updated = await svc.update(
            s.id, StrategyUpdate(name="renamed", params={"fast": 5}, max_position_qty=42)
        )
        assert updated.name == "renamed"
        assert updated.params == {"fast": 5}
        assert updated.max_position_qty == 42


@pytest.mark.asyncio
async def test_rename_collision_raises(session_factory) -> None:
    async with session_scope(session_factory) as session:
        svc = StrategyService(session)
        await svc.create(_create("name-a"))
        b = await svc.create(_create("name-b"))
        with pytest.raises(DuplicateStrategyError):
            await svc.update(b.id, StrategyUpdate(name="name-a"))


@pytest.mark.asyncio
async def test_halt_from_any_state(session_factory) -> None:
    async with session_scope(session_factory) as session:
        svc = StrategyService(session)
        s = await svc.create(_create())
        halted = await svc.halt(s.id)
        assert halted.state is StrategyState.HALTED


@pytest.mark.asyncio
async def test_delete_and_guard_live(session_factory) -> None:
    async with session_scope(session_factory) as session:
        svc = StrategyService(session)
        s = await svc.create(_create())
        # promote to LIVE then deletion is blocked
        for state in (StrategyState.BACKTESTING, StrategyState.PAPER, StrategyState.LIVE):
            await svc.update(s.id, StrategyUpdate(state=state))
        with pytest.raises(InvalidStateTransitionError):
            await svc.delete(s.id)
        # halt then delete succeeds
        await svc.halt(s.id)
        await svc.delete(s.id)
        with pytest.raises(StrategyNotFoundError):
            await svc.get(s.id)


@pytest.mark.asyncio
async def test_delete_missing_raises(session_factory) -> None:
    from uuid import uuid4

    async with session_scope(session_factory) as session:
        with pytest.raises(StrategyNotFoundError):
            await StrategyService(session).delete(uuid4())
