"""Outbox worker: drains index_outbox rows into Elasticsearch (ARCHITECTURE §2.3).

Runs as a background task inside the API process (started in app lifespan). Multiple
instances are safe: rows are claimed with SELECT … FOR UPDATE SKIP LOCKED. Rows that
keep failing beyond `outbox_max_attempts` are dead-lettered in place (processed_at
stays NULL, attempts caps them out of the scan) and logged for operator attention.
"""

import asyncio
from datetime import UTC, datetime
from typing import Any
from uuid import UUID

import structlog
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import Settings
from app.db.models.index_outbox import IndexOutbox
from app.db.models.product import Product
from app.db.session import async_session_factory
from app.services.indexing_service import IndexingService

logger = structlog.get_logger(__name__)


def collapse_ops(rows: list[IndexOutbox]) -> dict[str, str]:
    """Multiple pending rows per product collapse to the LAST operation (rows are
    id-ordered, so later writes win — e.g. upsert then delete → delete)."""
    ops: dict[str, str] = {}
    for row in rows:
        ops[str(row.product_id)] = row.op
    return ops


async def drain_pending(
    session: AsyncSession,
    indexing: IndexingService,
    *,
    batch_size: int = 200,
    max_attempts: int = 10,
) -> int:
    """Process one batch of pending outbox rows. Returns rows handled (0 = idle)."""
    rows = list(
        await session.scalars(
            select(IndexOutbox)
            .where(IndexOutbox.processed_at.is_(None), IndexOutbox.attempts < max_attempts)
            .order_by(IndexOutbox.id)
            .limit(batch_size)
            .with_for_update(skip_locked=True)
        )
    )
    if not rows:
        return 0

    ops = collapse_ops(rows)
    upsert_ids = [UUID(pid) for pid, op in ops.items() if op == "upsert"]
    products: dict[str, Product] = {}
    if upsert_ids:
        loaded = await session.scalars(select(Product).where(Product.id.in_(upsert_ids)))
        products = {str(p.id): p for p in loaded}

    failed: dict[str, str] = {}
    for product_id, op in ops.items():
        try:
            product = products.get(product_id)
            if op == "delete" or product is None or not product.is_active:
                # Vanished and soft-deleted products both mean "remove from the
                # index" — it must not keep documents the catalog no longer serves
                # (covers upsert rows enqueued before/while a product was deactivated).
                await indexing.delete_product(UUID(product_id))
            else:
                await indexing.index_product(product)
        except Exception as exc:  # noqa: BLE001 — one bad doc must not poison the batch
            failed[product_id] = str(exc)

    now = datetime.now(UTC)
    for row in rows:
        row.attempts += 1
        error = failed.get(str(row.product_id))
        if error is None:
            row.processed_at = now
            row.last_error = None
        else:
            row.last_error = error[:2000]
            if row.attempts >= max_attempts:
                logger.error("outbox_dead_letter", product_id=str(row.product_id), error=error)
    await session.commit()

    if failed:
        logger.warning("outbox_batch_partial", failed=len(failed), total=len(ops))
    else:
        logger.info("outbox_batch_processed", products=len(ops), rows=len(rows))
    return len(rows)


async def run_outbox_loop(es: Any, settings: Settings) -> None:
    """Poll-drain loop; drains eagerly while there is a backlog, then idles."""
    indexing = IndexingService(es=es, settings=settings)
    logger.info("outbox_worker_started", interval_s=settings.outbox_poll_interval_s)
    while True:
        try:
            async with async_session_factory() as session:
                handled = await drain_pending(
                    session,
                    indexing,
                    batch_size=settings.outbox_batch_size,
                    max_attempts=settings.outbox_max_attempts,
                )
        except asyncio.CancelledError:
            logger.info("outbox_worker_stopped")
            raise
        except Exception as exc:  # noqa: BLE001 — the loop must survive infra blips
            logger.warning("outbox_tick_failed", error=str(exc))
            handled = 0
        await asyncio.sleep(0.05 if handled else settings.outbox_poll_interval_s)
