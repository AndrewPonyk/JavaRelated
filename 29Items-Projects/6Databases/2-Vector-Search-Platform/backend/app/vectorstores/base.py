"""The ``VectorStore`` contract — the architectural seam of the platform.

Every backend (pgvector, Pinecone, Weaviate, Milvus) implements this ABC. Services depend
on this abstraction, never on a concrete backend, so:
  * adding a backend is one new file + one factory line, and
  * the same contract test suite runs against every backend (behavioral parity).

All vectors are expected to be **unit-normalized** and compared with **cosine** similarity,
so recall@k is comparable across backends (see TECH-NOTES §3.6).
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any


@dataclass(slots=True)
class VectorRecord:
    """A single item to index: an id, its embedding, text, and arbitrary metadata."""

    id: str
    vector: list[float]
    text: str = ""
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass(slots=True)
class SearchHit:
    """A single search result with a similarity ``score`` in [0, 1] (higher = closer)."""

    id: str
    score: float
    text: str = ""
    metadata: dict[str, Any] = field(default_factory=dict)


class VectorStore(ABC):
    """Uniform async interface over a vector database backend."""

    #: Canonical backend name, e.g. "pgvector". Used by the factory + logs.
    name: str = "base"

    @abstractmethod
    async def ensure_ready(self) -> None:
        """Create the collection/index if needed and verify connectivity + dim/metric."""

    @abstractmethod
    async def upsert(self, records: list[VectorRecord]) -> int:
        """Insert or update records. Returns the number of records written."""

    @abstractmethod
    async def query(
        self,
        vector: list[float],
        k: int = 10,
        *,
        filters: dict[str, Any] | None = None,
    ) -> list[SearchHit]:
        """Return the top-``k`` nearest neighbors, optionally filtered by metadata."""

    @abstractmethod
    async def delete(self, ids: list[str]) -> int:
        """Delete records by id. Returns the number of records removed."""

    @abstractmethod
    async def count(self) -> int:
        """Return the number of indexed vectors (best-effort)."""

    async def health(self) -> bool:
        """Lightweight liveness check for readiness probes. Override for cheaper checks."""
        try:
            await self.count()
        except Exception:
            return False
        return True

    async def close(self) -> None:
        """Release pools / clients. Default no-op; override where needed."""
        return None
