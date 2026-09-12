"""Position keeping and PnL projection.

``apply_fill`` is a pure function implementing signed average-cost accounting:
realized PnL is booked when a position is reduced/closed/flipped; the average
price is rolled when it is increased. This is the math the pnl-projector folds
``fills`` through (ARCHITECTURE §2.3.1).
"""

from __future__ import annotations

import abc
from decimal import Decimal

from trading_common.models.market import Fill, Position, Side


def apply_fill(position: Position, fill: Fill, *, mark: Decimal | None = None) -> Position:
    """Return a new Position reflecting ``fill`` applied to ``position``.

    ``mark`` is the price used for unrealized PnL; defaults to the fill price.
    """
    old_qty = position.quantity
    avg = position.avg_price
    realized = position.realized_pnl - fill.commission

    signed = fill.quantity if fill.side is Side.BUY else -fill.quantity
    new_qty = old_qty + signed

    increasing = old_qty == 0 or (old_qty > 0) == (signed > 0)
    if increasing:
        total_cost = abs(old_qty) * avg + fill.quantity * fill.price
        avg = total_cost / Decimal(abs(new_qty)) if new_qty != 0 else Decimal("0")
    else:
        direction = Decimal(1) if old_qty > 0 else Decimal(-1)
        closing_qty = min(fill.quantity, abs(old_qty))
        realized += direction * (fill.price - avg) * Decimal(closing_qty)
        if fill.quantity > abs(old_qty):  # flipped through zero
            avg = fill.price
        elif new_qty == 0:  # fully closed
            avg = Decimal("0")
        # else: partial reduction — average price unchanged

    mark_price = mark if mark is not None else fill.price
    unrealized = (mark_price - avg) * Decimal(new_qty) if new_qty != 0 else Decimal("0")

    return Position(
        symbol=position.symbol,
        quantity=new_qty,
        avg_price=avg,
        realized_pnl=realized,
        unrealized_pnl=unrealized,
    )


class PositionStore(abc.ABC):
    """Authoritative live-position state (hot path reads in prod come from Redis)."""

    @abc.abstractmethod
    async def get(self, symbol: str) -> Position: ...

    @abc.abstractmethod
    async def put(self, position: Position) -> None: ...

    @abc.abstractmethod
    async def all(self) -> list[Position]: ...


class InMemoryPositionStore(PositionStore):
    def __init__(self) -> None:
        self._positions: dict[str, Position] = {}

    async def get(self, symbol: str) -> Position:
        return self._positions.get(symbol, Position(symbol=symbol))

    async def put(self, position: Position) -> None:
        self._positions[position.symbol] = position

    async def all(self) -> list[Position]:
        return list(self._positions.values())


class RedisPositionStore(PositionStore):  # pragma: no cover - needs a Redis server
    """Redis-backed store for distributed deployments."""

    def __init__(self, url: str, namespace: str = "pos") -> None:
        import redis.asyncio as redis

        self._redis = redis.from_url(url)
        self._ns = namespace

    def _key(self, symbol: str) -> str:
        return f"{self._ns}:{symbol}"

    async def get(self, symbol: str) -> Position:
        raw = await self._redis.get(self._key(symbol))
        return Position.model_validate_json(raw) if raw else Position(symbol=symbol)

    async def put(self, position: Position) -> None:
        await self._redis.set(self._key(position.symbol), position.model_dump_json())

    async def all(self) -> list[Position]:
        keys = await self._redis.keys(f"{self._ns}:*")
        out: list[Position] = []
        for k in keys:
            raw = await self._redis.get(k)
            if raw:
                out.append(Position.model_validate_json(raw))
        return out


class Projector:
    """Folds fills into the position store (the pnl-projector's core)."""

    def __init__(self, store: PositionStore) -> None:
        self._store = store
        self._marks: dict[str, Decimal] = {}

    async def on_fill(self, fill: Fill) -> Position:
        current = await self._store.get(fill.symbol)
        self._marks[fill.symbol] = fill.price
        updated = apply_fill(current, fill, mark=self._marks.get(fill.symbol))
        await self._store.put(updated)
        return updated

    async def on_mark(self, symbol: str, price: Decimal) -> None:
        """Update unrealized PnL when a new market price arrives."""
        self._marks[symbol] = price
        current = await self._store.get(symbol)
        if current.quantity != 0:
            current = Position(
                symbol=current.symbol,
                quantity=current.quantity,
                avg_price=current.avg_price,
                realized_pnl=current.realized_pnl,
                unrealized_pnl=(price - current.avg_price) * Decimal(current.quantity),
            )
            await self._store.put(current)
