"""In-process vector backend — a real, exact cosine-similarity store.

Not a mock: it performs correct brute-force nearest-neighbor search and metadata filtering.
Ideal for local dev, tests, and small corpora; it is the default ``memory`` backend.
"""

from __future__ import annotations

import math
from typing import Any

import structlog

from app.vectorstores.base import SearchHit, VectorRecord, VectorStore

log = structlog.get_logger(__name__)


def _cosine(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b, strict=False))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(x * x for x in b))
    if na == 0 or nb == 0:
        return 0.0
    return dot / (na * nb)


def _matches(metadata: dict[str, Any], filters: dict[str, Any] | None) -> bool:
    if not filters:
        return True
    return all(metadata.get(key) == value for key, value in filters.items())


class InMemoryVectorStore(VectorStore):
    name = "memory"

    def __init__(self, dim: int) -> None:
        self._dim = dim
        self._data: dict[str, VectorRecord] = {}

    async def ensure_ready(self) -> None:
        return None

    async def upsert(self, records: list[VectorRecord]) -> int:
        for record in records:
            if len(record.vector) != self._dim:
                raise ValueError(
                    f"vector dim {len(record.vector)} != store dim {self._dim} for id={record.id}"
                )
            self._data[record.id] = record
        return len(records)

    async def query(
        self,
        vector: list[float],
        k: int = 10,
        *,
        filters: dict[str, Any] | None = None,
    ) -> list[SearchHit]:
        scored = [
            SearchHit(id=r.id, score=_cosine(vector, r.vector), text=r.text, metadata=r.metadata)
            for r in self._data.values()
            if _matches(r.metadata, filters)
        ]
        scored.sort(key=lambda h: h.score, reverse=True)
        return scored[:k]

    async def delete(self, ids: list[str]) -> int:
        removed = 0
        for identifier in ids:
            if self._data.pop(identifier, None) is not None:
                removed += 1
        return removed

    async def count(self) -> int:
        return len(self._data)

    async def clear(self) -> None:
        """Test helper: drop all records."""
        self._data.clear()
