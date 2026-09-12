"""Pinecone backend adapter (managed SaaS).

Feature-flagged (``ENABLE_PINECONE``). The SDK is synchronous, so calls are dispatched to a
threadpool to keep the event loop free. Imports are guarded so the app boots without the SDK.
"""

from __future__ import annotations

from functools import partial
from typing import Any

import anyio
import structlog

from app.core.exceptions import BackendUnavailableError
from app.vectorstores.base import SearchHit, VectorRecord, VectorStore

log = structlog.get_logger(__name__)


class PineconeStore(VectorStore):
    name = "pinecone"

    def __init__(
        self,
        api_key: str,
        index: str,
        *,
        dim: int,
        cloud: str = "aws",
        region: str = "us-east-1",
    ) -> None:
        self._api_key = api_key
        self._index_name = index
        self._dim = dim
        self._cloud = cloud
        self._region = region
        self._index: Any | None = None

    def _connect(self) -> Any:
        try:
            from pinecone import Pinecone, ServerlessSpec
        except ImportError as exc:  # pragma: no cover
            raise BackendUnavailableError("pinecone-client not installed.") from exc

        pc = Pinecone(api_key=self._api_key)
        existing = {i["name"] for i in pc.list_indexes()}
        if self._index_name not in existing:
            pc.create_index(
                name=self._index_name,
                dimension=self._dim,
                metric="cosine",
                spec=ServerlessSpec(cloud=self._cloud, region=self._region),
            )
        return pc.Index(self._index_name)

    async def ensure_ready(self) -> None:
        if self._index is None:
            self._index = await anyio.to_thread.run_sync(self._connect)

    async def upsert(self, records: list[VectorRecord]) -> int:
        index = self._require_index()
        vectors: list[dict[str, Any]] = [
            {"id": r.id, "values": r.vector, "metadata": {**r.metadata, "text": r.text}}
            for r in records
        ]
        # Pinecone recommends batches of <= 100 vectors.
        for start in range(0, len(vectors), 100):
            batch = vectors[start : start + 100]
            await anyio.to_thread.run_sync(partial(index.upsert, vectors=batch))
        return len(vectors)

    async def query(
        self,
        vector: list[float],
        k: int = 10,
        *,
        filters: dict[str, Any] | None = None,
    ) -> list[SearchHit]:
        index = self._require_index()
        res = await anyio.to_thread.run_sync(
            lambda: index.query(
                vector=vector, top_k=k, include_metadata=True, filter=filters or None
            )
        )
        hits: list[SearchHit] = []
        for match in res.get("matches", []):
            meta = dict(match.get("metadata") or {})
            hits.append(
                SearchHit(
                    id=match["id"],
                    score=float(match["score"]),
                    text=str(meta.pop("text", "")),
                    metadata=meta,
                )
            )
        return hits

    async def delete(self, ids: list[str]) -> int:
        index = self._require_index()
        await anyio.to_thread.run_sync(lambda: index.delete(ids=ids))
        return len(ids)

    async def count(self) -> int:
        index = self._require_index()
        stats = await anyio.to_thread.run_sync(index.describe_index_stats)
        return int(stats.get("total_vector_count", 0))

    def _require_index(self) -> Any:
        if self._index is None:
            raise BackendUnavailableError("Pinecone index not initialized; call ensure_ready().")
        return self._index
