"""Vector-store providers.

``InMemoryVectorStore`` is a real namespaced cosine index (process-global, thread-safe).
``PineconeVectorStore`` wraps Pinecone via LangChain for production. Per-tenant
**namespaces** enforce isolation — every read/write/delete is scoped to a namespace so
cross-tenant retrieval is impossible by construction (ARCHITECTURE §2.5).
"""

from __future__ import annotations

import threading
from collections import defaultdict
from functools import lru_cache

from app.core.config import settings
from app.rag.errors import RetrievalError
from app.rag.types import RetrievedChunk, VectorRecord, VectorStore


def _cosine(a: list[float], b: list[float]) -> float:
    # Vectors from our embedders are L2-normalized → dot product is cosine similarity.
    return sum(x * y for x, y in zip(a, b, strict=False))


def _matches(metadata: dict, flt: dict | None) -> bool:
    if not flt:
        return True
    return all(metadata.get(k) == v for k, v in flt.items())


class InMemoryVectorStore:
    """A namespaced in-memory cosine index. State lives for the process lifetime."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        # namespace -> id -> VectorRecord
        self._data: dict[str, dict[str, VectorRecord]] = defaultdict(dict)

    async def aupsert(self, namespace: str, records: list[VectorRecord]) -> None:
        if not namespace:
            raise RetrievalError("namespace (tenant id) is required for isolation")
        with self._lock:
            ns = self._data[namespace]
            for rec in records:
                ns[rec.id] = rec

    async def aquery(
        self, namespace: str, vector: list[float], top_k: int, flt: dict | None = None
    ) -> list[RetrievedChunk]:
        with self._lock:
            records = list(self._data.get(namespace, {}).values())
        scored = [
            RetrievedChunk(
                id=r.id, text=r.text, score=_cosine(vector, r.vector), metadata=r.metadata
            )
            for r in records
            if _matches(r.metadata, flt)
        ]
        scored.sort(key=lambda c: c.score, reverse=True)
        return scored[:top_k]

    async def adelete_document(self, namespace: str, document_id: str) -> None:
        with self._lock:
            ns = self._data.get(namespace, {})
            for key in [k for k, r in ns.items() if r.metadata.get("document_id") == document_id]:
                del ns[key]


class PineconeVectorStore:
    """Pinecone index access via the official client (namespaced)."""

    def __init__(self) -> None:
        from pinecone import Pinecone  # lazy: prod-only dependency

        self._pc = Pinecone(api_key=settings.pinecone_api_key)
        self._index = self._pc.Index(settings.pinecone_index)

    async def aupsert(self, namespace: str, records: list[VectorRecord]) -> None:
        if not namespace:
            raise RetrievalError("namespace (tenant id) is required for isolation")
        vectors = [
            {"id": r.id, "values": r.vector, "metadata": {**r.metadata, "text": r.text}}
            for r in records
        ]
        try:
            self._index.upsert(vectors=vectors, namespace=namespace)
        except Exception as exc:  # noqa: BLE001
            raise RetrievalError(str(exc)) from exc

    async def aquery(
        self, namespace: str, vector: list[float], top_k: int, flt: dict | None = None
    ) -> list[RetrievedChunk]:
        try:
            res = self._index.query(
                vector=vector,
                top_k=top_k,
                namespace=namespace,
                filter=flt or None,
                include_metadata=True,
            )
        except Exception as exc:  # noqa: BLE001
            raise RetrievalError(str(exc)) from exc
        chunks: list[RetrievedChunk] = []
        for match in res.get("matches", []):
            meta = dict(match.get("metadata") or {})
            text = meta.pop("text", "")
            chunks.append(
                RetrievedChunk(
                    id=match["id"], text=text, score=float(match["score"]), metadata=meta
                )
            )
        return chunks

    async def adelete_document(self, namespace: str, document_id: str) -> None:
        try:
            self._index.delete(filter={"document_id": document_id}, namespace=namespace)
        except Exception as exc:  # noqa: BLE001
            raise RetrievalError(str(exc)) from exc


@lru_cache
def get_vector_store() -> VectorStore:
    if settings.rag_backend == "bedrock":
        return PineconeVectorStore()
    return InMemoryVectorStore()


# Test/maintenance helper: drop all in-memory state.
def reset_in_memory_store() -> None:
    store = get_vector_store()
    if isinstance(store, InMemoryVectorStore):
        with store._lock:  # noqa: SLF001 — internal test hook
            store._data.clear()
