"""Outbox drain semantics: op collapsing, success marking, failure retention."""

import uuid
from datetime import UTC, datetime
from decimal import Decimal
from unittest.mock import AsyncMock

from app.db.models.index_outbox import IndexOutbox
from app.db.models.product import Product
from app.services.outbox_worker import collapse_ops, drain_pending


def make_row(row_id: int, product_id: str, op: str) -> IndexOutbox:
    return IndexOutbox(id=row_id, product_id=product_id, op=op, attempts=0)


def make_product(product_id: uuid.UUID) -> Product:
    return Product(  # type: ignore[call-arg]
        id=product_id,
        sku=f"SKU-{product_id.hex[:6]}",
        name="Thing",
        price=Decimal("10.00"),
        attributes={},
        popularity=1.0,
        in_stock=True,
        is_active=True,
        created_at=datetime(2026, 1, 1, tzinfo=UTC),
    )


def test_collapse_keeps_last_operation_per_product() -> None:
    pid = str(uuid.uuid4())
    rows = [make_row(1, pid, "upsert"), make_row(2, pid, "delete")]
    assert collapse_ops(rows) == {pid: "delete"}


def _session_with(rows: list[IndexOutbox], products: list[Product]) -> AsyncMock:
    session = AsyncMock()
    session.scalars.side_effect = [rows, products]
    return session


async def test_drain_upserts_and_deletes_then_marks_processed() -> None:
    upsert_id, delete_id = uuid.uuid4(), uuid.uuid4()
    rows = [make_row(1, str(upsert_id), "upsert"), make_row(2, str(delete_id), "delete")]
    product = make_product(upsert_id)
    session = _session_with(rows, [product])
    indexing = AsyncMock()

    handled = await drain_pending(session, indexing)

    assert handled == 2
    indexing.index_product.assert_awaited_once_with(product)
    indexing.delete_product.assert_awaited_once_with(delete_id)
    assert all(row.processed_at is not None for row in rows)
    assert all(row.attempts == 1 for row in rows)
    session.commit.assert_awaited_once()


async def test_drain_treats_vanished_product_as_delete() -> None:
    ghost_id = uuid.uuid4()
    rows = [make_row(1, str(ghost_id), "upsert")]
    session = _session_with(rows, [])  # product no longer in PG
    indexing = AsyncMock()

    await drain_pending(session, indexing)

    indexing.delete_product.assert_awaited_once_with(ghost_id)
    indexing.index_product.assert_not_awaited()
    assert rows[0].processed_at is not None


async def test_drain_treats_inactive_product_upsert_as_delete() -> None:
    """An upsert enqueued before (or during) deactivation must not resurrect the
    document — soft-deleted products get removed from the index, not re-indexed."""
    pid = uuid.uuid4()
    product = make_product(pid)
    product.is_active = False
    rows = [make_row(1, str(pid), "upsert")]
    session = _session_with(rows, [product])
    indexing = AsyncMock()

    await drain_pending(session, indexing)

    indexing.delete_product.assert_awaited_once_with(pid)
    indexing.index_product.assert_not_awaited()


async def test_drain_keeps_failed_rows_pending_with_error() -> None:
    pid = uuid.uuid4()
    rows = [make_row(1, str(pid), "upsert")]
    session = _session_with(rows, [make_product(pid)])
    indexing = AsyncMock()
    indexing.index_product.side_effect = ConnectionError("es down")

    await drain_pending(session, indexing)

    assert rows[0].processed_at is None  # stays pending -> retried next tick
    assert rows[0].attempts == 1
    assert "es down" in (rows[0].last_error or "")
    session.commit.assert_awaited_once()


async def test_drain_idles_on_empty_backlog() -> None:
    session = AsyncMock()
    session.scalars.side_effect = [[]]
    assert await drain_pending(session, AsyncMock()) == 0
    session.commit.assert_not_awaited()
