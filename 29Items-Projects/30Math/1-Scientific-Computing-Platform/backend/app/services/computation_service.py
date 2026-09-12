"""Persistence + dispatch logic for saved computations. Owner-scoped always."""

from __future__ import annotations

import logging
from uuid import UUID

from anyio import to_thread
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models import Computation
from app.schemas.computation import ComputationCreate

logger = logging.getLogger(__name__)


async def create(session: AsyncSession, *, owner_id: UUID, data: ComputationCreate) -> Computation:
    computation = Computation(
        owner_id=owner_id,
        title=data.title,
        kind=data.kind,
        status="queued",
        input_payload=data.input_payload,
    )
    session.add(computation)
    await session.commit()
    await session.refresh(computation)

    # Publishing to the broker is blocking I/O (and runs the whole task inline
    # in eager test mode) — keep it off the event loop.
    await to_thread.run_sync(_enqueue, computation)
    await session.refresh(computation)  # eager mode may have completed it already
    return computation


def _enqueue(computation: Computation) -> None:
    """Hand the computation to the compute plane, idempotency key = row id.

    A broker outage must not lose work silently: the row is marked failed with
    a distinct error_code so the client sees a terminal state immediately.
    """
    from app.workers.tasks import run_computation  # lazy: keeps API import light

    try:
        run_computation.delay(str(computation.id))
    except Exception:
        logger.error(
            "enqueue failed for computation %s; marking failed", computation.id, exc_info=True
        )
        from app.db.sync_session import sync_session

        with sync_session() as session:
            row = session.get(Computation, computation.id)
            if row is not None and row.status == "queued":
                row.status = "failed"
                row.error_code = "queue_unavailable"
                row.result_payload = {"error": "Compute queue is unavailable; try again later."}
                session.commit()


async def get(session: AsyncSession, *, owner_id: UUID, computation_id: UUID) -> Computation | None:
    stmt = select(Computation).where(
        Computation.id == computation_id,
        Computation.owner_id == owner_id,  # ownership scoping — not optional
    )
    return (await session.execute(stmt)).scalar_one_or_none()


async def list_for_owner(
    session: AsyncSession, *, owner_id: UUID, limit: int, offset: int
) -> tuple[list[Computation], int]:
    base = select(Computation).where(Computation.owner_id == owner_id)
    total = (await session.execute(select(func.count()).select_from(base.subquery()))).scalar_one()
    rows = (
        (
            await session.execute(
                base.order_by(Computation.created_at.desc(), Computation.id)
                .limit(limit)
                .offset(offset)
            )
        )
        .scalars()
        .all()
    )
    return list(rows), int(total)


async def delete(session: AsyncSession, *, owner_id: UUID, computation_id: UUID) -> bool:
    computation = await get(session, owner_id=owner_id, computation_id=computation_id)
    if computation is None:
        return False
    await session.delete(computation)
    await session.commit()
    return True
