"""Operational endpoints (API-key protected)."""

from fastapi import APIRouter, Depends, status
from sqlalchemy import insert, literal, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_db, require_admin_key
from app.db.models.index_outbox import IndexOutbox
from app.db.models.product import Product

router = APIRouter(dependencies=[Depends(require_admin_key)])


@router.post("/reindex", status_code=status.HTTP_202_ACCEPTED, summary="Trigger full re-sync")
async def trigger_reindex(db: AsyncSession = Depends(get_db)) -> dict[str, int | str]:
    """Re-enqueue the whole catalog through the outbox: every active product is
    upserted and every inactive one deleted from the index. The outbox worker drains
    the backlog within its normal poll loop. For mapping/analyzer changes that need
    a NEW index, use scripts/reindex.py (versioned index + alias swap) instead."""
    upserts = await db.execute(
        insert(IndexOutbox).from_select(
            ["product_id", "op"],
            select(Product.id, literal("upsert")).where(Product.is_active.is_(True)),
        )
    )
    deletes = await db.execute(
        insert(IndexOutbox).from_select(
            ["product_id", "op"],
            select(Product.id, literal("delete")).where(Product.is_active.is_(False)),
        )
    )
    await db.commit()
    enqueued = (upserts.rowcount or 0) + (deletes.rowcount or 0)
    return {"status": "accepted", "enqueued": enqueued}
