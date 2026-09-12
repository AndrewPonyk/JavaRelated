"""Resolve a backend name -> a ``VectorStore`` instance.

This is the *only* place concrete backends are imported. Adding a backend = one entry here.
Instances are cached per (backend, dim) so pools/clients + in-memory data are reused across
requests within a process.
"""

from __future__ import annotations

from functools import cache

from app.core.config import Backend, Settings, get_settings
from app.core.exceptions import BackendUnavailableError
from app.vectorstores.base import VectorStore
from app.vectorstores.memory_store import InMemoryVectorStore
from app.vectorstores.milvus_store import MilvusStore
from app.vectorstores.pgvector_store import PgVectorStore
from app.vectorstores.pinecone_store import PineconeStore
from app.vectorstores.weaviate_store import WeaviateStore


def _build(backend: Backend, settings: Settings) -> VectorStore:
    dim = settings.embedding_dim

    if backend is Backend.memory:
        return InMemoryVectorStore(dim)

    if backend is Backend.pgvector:
        return PgVectorStore(settings.database_url, dim=dim)

    if backend is Backend.pinecone:
        if not settings.enable_pinecone or not settings.pinecone_api_key:
            raise BackendUnavailableError("Pinecone is disabled or missing PINECONE_API_KEY.")
        return PineconeStore(settings.pinecone_api_key, settings.pinecone_index, dim=dim)

    if backend is Backend.weaviate:
        if not settings.enable_weaviate:
            raise BackendUnavailableError("Weaviate is disabled (ENABLE_WEAVIATE=false).")
        return WeaviateStore(
            settings.weaviate_url,
            dim=dim,
            api_key=settings.weaviate_api_key,
            collection=settings.weaviate_collection,
        )

    if backend is Backend.milvus:
        if not settings.enable_milvus:
            raise BackendUnavailableError("Milvus is disabled (ENABLE_MILVUS=false).")
        return MilvusStore(settings.milvus_uri, settings.milvus_collection, dim=dim)

    raise BackendUnavailableError(f"Unknown backend: {backend!r}")


@cache
def _cached_store(backend: Backend, dim: int) -> VectorStore:
    # dim is part of the key so a config change rebuilds the instance.
    return _build(backend, get_settings())


def get_vector_store(backend: Backend | str | None = None) -> VectorStore:
    """Return a (cached) VectorStore for ``backend`` (defaults to configured backend)."""
    settings = get_settings()
    resolved = Backend(backend) if backend is not None else settings.default_backend
    return _cached_store(resolved, settings.embedding_dim)


def reset_stores() -> None:
    """Drop the cached store instances (used by tests / config reloads)."""
    _cached_store.cache_clear()
