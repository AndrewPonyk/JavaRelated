"""Unit tests for the vector-store factory (backend resolution + feature flags)."""

from __future__ import annotations

import pytest
from app.core.config import Backend
from app.core.exceptions import BackendUnavailableError
from app.vectorstores.factory import get_vector_store, reset_stores
from app.vectorstores.memory_store import InMemoryVectorStore
from app.vectorstores.pgvector_store import PgVectorStore


def test_memory_backend_builds() -> None:
    reset_stores()
    store = get_vector_store(Backend.memory)
    assert isinstance(store, InMemoryVectorStore)


def test_default_backend_used_when_none() -> None:
    # Tests configure DEFAULT_BACKEND=memory.
    assert get_vector_store().name == "memory"


def test_pgvector_builds_without_connecting() -> None:
    store = get_vector_store(Backend.pgvector)
    assert isinstance(store, PgVectorStore)
    assert store.name == "pgvector"


def test_same_backend_is_cached() -> None:
    reset_stores()
    assert get_vector_store(Backend.memory) is get_vector_store(Backend.memory)


def test_string_backend_name_accepted() -> None:
    assert get_vector_store("memory").name == "memory"


@pytest.mark.parametrize("backend", [Backend.pinecone, Backend.weaviate, Backend.milvus])
def test_disabled_backends_raise(backend: Backend) -> None:
    reset_stores()
    with pytest.raises(BackendUnavailableError):
        get_vector_store(backend)
