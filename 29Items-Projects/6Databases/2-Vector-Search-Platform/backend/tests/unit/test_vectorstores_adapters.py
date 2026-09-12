"""Unit tests for the external adapters' pure logic + error guards.

These don't need the vendor SDKs or live services: they exercise DSN/URL parsing, deterministic
id derivation, and the "not initialized / SDK missing" guards that must raise cleanly.
"""

from __future__ import annotations

import pytest
from app.core.exceptions import BackendUnavailableError
from app.vectorstores.base import VectorRecord
from app.vectorstores.milvus_store import MilvusStore
from app.vectorstores.pgvector_store import PgVectorStore, _to_asyncpg_dsn
from app.vectorstores.pinecone_store import PineconeStore
from app.vectorstores.weaviate_store import WeaviateStore, _deterministic_uuid


def test_to_asyncpg_dsn_strips_driver() -> None:
    assert _to_asyncpg_dsn("postgresql+asyncpg://u:p@h:5432/db") == "postgresql://u:p@h:5432/db"
    assert _to_asyncpg_dsn("postgresql://u:p@h/db") == "postgresql://u:p@h/db"


@pytest.mark.asyncio
async def test_pgvector_operations_require_pool() -> None:
    store = PgVectorStore("postgresql+asyncpg://u:p@h/db", dim=4)
    vec = [0.0, 0.0, 0.0, 0.0]
    with pytest.raises(BackendUnavailableError):
        await store.query(vec, k=1)
    with pytest.raises(BackendUnavailableError):
        await store.upsert([VectorRecord(id="a", vector=vec)])
    with pytest.raises(BackendUnavailableError):
        await store.delete(["a"])
    with pytest.raises(BackendUnavailableError):
        await store.count()


def test_weaviate_url_parsing() -> None:
    store = WeaviateStore("http://weav-host:1234", dim=4, collection="C")
    assert store._host == "weav-host"
    assert store._port == 1234


def test_weaviate_deterministic_uuid() -> None:
    assert _deterministic_uuid("d1:0") == _deterministic_uuid("d1:0")
    assert _deterministic_uuid("d1:0") != _deterministic_uuid("d1:1")


def test_weaviate_requires_client() -> None:
    with pytest.raises(BackendUnavailableError):
        WeaviateStore("http://localhost:8080", dim=4)._require_client()


def test_milvus_requires_client() -> None:
    with pytest.raises(BackendUnavailableError):
        MilvusStore("http://localhost:19530", "c", dim=4)._require_client()


def test_pinecone_requires_index() -> None:
    with pytest.raises(BackendUnavailableError):
        PineconeStore("key", "idx", dim=4)._require_index()


@pytest.mark.asyncio
async def test_weaviate_ensure_ready_without_sdk_raises() -> None:
    # weaviate-client is not installed in the test env -> guarded import -> BackendUnavailableError.
    with pytest.raises(BackendUnavailableError):
        await WeaviateStore("http://localhost:8080", dim=4).ensure_ready()


@pytest.mark.asyncio
async def test_milvus_ensure_ready_without_sdk_raises() -> None:
    with pytest.raises(BackendUnavailableError):
        await MilvusStore("http://localhost:19530", "c", dim=4).ensure_ready()


@pytest.mark.asyncio
async def test_pinecone_ensure_ready_without_sdk_raises() -> None:
    with pytest.raises(BackendUnavailableError):
        await PineconeStore("key", "idx", dim=4).ensure_ready()
