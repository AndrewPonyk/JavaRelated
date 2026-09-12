"""Strategy business logic over the persistence layer.

Routers stay thin; lifecycle rules (valid state transitions, kill-switch,
name uniqueness, delete guards) live here and are unit-tested against a real
(SQLite) database.
"""

from __future__ import annotations

from decimal import Decimal
from uuid import UUID

from sqlalchemy.ext.asyncio import AsyncSession

from api_gateway.schemas.strategy import (
    StrategyCreate,
    StrategyOut,
    StrategyState,
    StrategyUpdate,
)
from trading_common.db import StrategyRepository, StrategyRow

# Allowed lifecycle transitions; halting is always permitted (handled separately).
_ALLOWED_TRANSITIONS: dict[StrategyState, set[StrategyState]] = {
    StrategyState.DRAFT: {StrategyState.BACKTESTING, StrategyState.HALTED},
    StrategyState.BACKTESTING: {StrategyState.PAPER, StrategyState.HALTED},
    StrategyState.PAPER: {StrategyState.LIVE, StrategyState.HALTED},
    StrategyState.LIVE: {StrategyState.HALTED},
    StrategyState.HALTED: {StrategyState.PAPER, StrategyState.DRAFT},
}


class StrategyNotFoundError(Exception):
    pass


class DuplicateStrategyError(Exception):
    pass


class InvalidStateTransitionError(Exception):
    pass


def _to_out(row: StrategyRow) -> StrategyOut:
    # from_attributes maps row.klass->klass, coerces str->StrategyState, Decimal->float.
    return StrategyOut.model_validate(row)


class StrategyService:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session
        self._repo = StrategyRepository(session)

    async def list(self) -> list[StrategyOut]:
        return [_to_out(r) for r in await self._repo.list()]

    async def get(self, strategy_id: UUID) -> StrategyOut:
        row = await self._repo.get(strategy_id)
        if row is None:
            raise StrategyNotFoundError(str(strategy_id))
        return _to_out(row)

    async def create(self, payload: StrategyCreate) -> StrategyOut:
        if await self._repo.get_by_name(payload.name) is not None:
            raise DuplicateStrategyError(payload.name)
        row = StrategyRow(
            name=payload.name,
            klass=payload.klass,
            symbols=payload.normalized_symbols,
            params=payload.params,
            state=StrategyState.DRAFT.value,
            max_position_qty=payload.max_position_qty,
            max_order_notional=Decimal(str(payload.max_order_notional)),
        )
        await self._repo.add(row)
        return _to_out(row)

    async def update(self, strategy_id: UUID, patch: StrategyUpdate) -> StrategyOut:
        row = await self._repo.get(strategy_id)
        if row is None:
            raise StrategyNotFoundError(str(strategy_id))

        if patch.state is not None and patch.state.value != row.state:
            self._assert_transition(StrategyState(row.state), patch.state)
            row.state = patch.state.value
        if patch.name is not None:
            existing = await self._repo.get_by_name(patch.name)
            if existing is not None and existing.id != row.id:
                raise DuplicateStrategyError(patch.name)
            row.name = patch.name
        if patch.params is not None:
            row.params = patch.params
        if patch.max_position_qty is not None:
            row.max_position_qty = patch.max_position_qty
        if patch.max_order_notional is not None:
            row.max_order_notional = Decimal(str(patch.max_order_notional))

        await self._session.flush()  # persist mutations within the request txn
        return _to_out(row)

    async def delete(self, strategy_id: UUID) -> None:
        row = await self._repo.get(strategy_id)
        if row is None:
            raise StrategyNotFoundError(str(strategy_id))
        if row.state == StrategyState.LIVE.value:
            raise InvalidStateTransitionError("cannot delete a LIVE strategy; HALT it first")
        await self._repo.delete(row)

    async def halt(self, strategy_id: UUID) -> StrategyOut:
        """Kill-switch: force HALTED from any state."""
        row = await self._repo.get(strategy_id)
        if row is None:
            raise StrategyNotFoundError(str(strategy_id))
        row.state = StrategyState.HALTED.value
        await self._session.flush()
        return _to_out(row)

    @staticmethod
    def _assert_transition(src: StrategyState, dst: StrategyState) -> None:
        if dst is StrategyState.HALTED:
            return
        if dst not in _ALLOWED_TRANSITIONS.get(src, set()):
            raise InvalidStateTransitionError(f"{src.value} -> {dst.value} is not allowed")
