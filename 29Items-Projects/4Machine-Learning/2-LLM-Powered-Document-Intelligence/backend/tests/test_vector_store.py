"""Unit tests for the in-memory vector store (namespacing, filtering, deletion)."""

from __future__ import annotations

import pytest

from app.rag.errors import RetrievalError
from app.rag.types import VectorRecord
from app.rag.vector_store import InMemoryVectorStore


def _rec(rid: str, vec: list[float], **meta) -> VectorRecord:
    return VectorRecord(id=rid, vector=vec, text=f"text-{rid}", metadata=meta)


@pytest.mark.asyncio
async def test_query_returns_top_k_by_cosine() -> None:
    store = InMemoryVectorStore()
    await store.aupsert(
        "t1",
        [
            _rec("a", [1.0, 0.0], document_id="d1", chunk_index=0),
            _rec("b", [0.0, 1.0], document_id="d1", chunk_index=1),
        ],
    )
    results = await store.aquery("t1", [1.0, 0.0], top_k=1)
    assert len(results) == 1
    assert results[0].id == "a"


@pytest.mark.asyncio
async def test_namespaces_isolate_tenants() -> None:
    store = InMemoryVectorStore()
    await store.aupsert("tenant-a", [_rec("a", [1.0, 0.0], document_id="d1")])
    await store.aupsert("tenant-b", [_rec("b", [1.0, 0.0], document_id="d2")])

    a_results = await store.aquery("tenant-a", [1.0, 0.0], top_k=10)
    assert {r.id for r in a_results} == {"a"}  # tenant-b's vector is invisible


@pytest.mark.asyncio
async def test_metadata_filter() -> None:
    store = InMemoryVectorStore()
    await store.aupsert(
        "t1",
        [
            _rec("legal", [1.0, 0.0], document_id="d1", doc_type="legal"),
            _rec("medical", [1.0, 0.0], document_id="d2", doc_type="medical"),
        ],
    )
    results = await store.aquery("t1", [1.0, 0.0], top_k=10, flt={"doc_type": "legal"})
    assert {r.id for r in results} == {"legal"}


@pytest.mark.asyncio
async def test_delete_document_removes_all_its_chunks() -> None:
    store = InMemoryVectorStore()
    await store.aupsert(
        "t1",
        [
            _rec("d1:0", [1.0, 0.0], document_id="d1"),
            _rec("d1:1", [0.9, 0.1], document_id="d1"),
            _rec("d2:0", [0.0, 1.0], document_id="d2"),
        ],
    )
    await store.adelete_document("t1", "d1")
    remaining = await store.aquery("t1", [1.0, 0.0], top_k=10)
    assert {r.id for r in remaining} == {"d2:0"}


@pytest.mark.asyncio
async def test_upsert_requires_namespace() -> None:
    store = InMemoryVectorStore()
    with pytest.raises(RetrievalError):
        await store.aupsert("", [_rec("a", [1.0])])
