"""Unit tests for the in-memory vector backend (real cosine search)."""

from __future__ import annotations

import pytest
from app.vectorstores.base import VectorRecord
from app.vectorstores.memory_store import InMemoryVectorStore


def _rec(id_: str, vec: list[float], **meta: object) -> VectorRecord:
    return VectorRecord(id=id_, vector=vec, text=f"text-{id_}", metadata=dict(meta))


@pytest.mark.asyncio
async def test_upsert_and_count() -> None:
    store = InMemoryVectorStore(dim=3)
    n = await store.upsert([_rec("a", [1, 0, 0]), _rec("b", [0, 1, 0])])
    assert n == 2
    assert await store.count() == 2


@pytest.mark.asyncio
async def test_query_orders_by_cosine_similarity() -> None:
    store = InMemoryVectorStore(dim=3)
    await store.upsert([_rec("a", [1, 0, 0]), _rec("b", [0, 1, 0]), _rec("c", [0.9, 0.1, 0])])
    hits = await store.query([1, 0, 0], k=2)
    assert [h.id for h in hits] == ["a", "c"]
    assert hits[0].score == pytest.approx(1.0)


@pytest.mark.asyncio
async def test_metadata_filter() -> None:
    store = InMemoryVectorStore(dim=2)
    await store.upsert([_rec("a", [1, 0], lang="en"), _rec("b", [1, 0], lang="fr")])
    hits = await store.query([1, 0], k=10, filters={"lang": "fr"})
    assert [h.id for h in hits] == ["b"]


@pytest.mark.asyncio
async def test_delete() -> None:
    store = InMemoryVectorStore(dim=2)
    await store.upsert([_rec("a", [1, 0]), _rec("b", [0, 1])])
    removed = await store.delete(["a", "missing"])
    assert removed == 1
    assert await store.count() == 1


@pytest.mark.asyncio
async def test_dim_mismatch_raises() -> None:
    store = InMemoryVectorStore(dim=3)
    with pytest.raises(ValueError, match="dim"):
        await store.upsert([_rec("a", [1, 0])])
