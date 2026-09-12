"""Pluggable vector database backends behind one ``VectorStore`` contract."""

from app.vectorstores.base import SearchHit, VectorRecord, VectorStore
from app.vectorstores.factory import get_vector_store, reset_stores
from app.vectorstores.memory_store import InMemoryVectorStore

__all__ = [
    "InMemoryVectorStore",
    "SearchHit",
    "VectorRecord",
    "VectorStore",
    "get_vector_store",
    "reset_stores",
]
