"""Milvus backend adapter (pymilvus ``MilvusClient``).

Feature-flagged (``ENABLE_MILVUS``). Uses cosine metric so recall is comparable across
backends. Sync client calls run in a threadpool; imports are guarded so the app boots
without the SDK.
"""

from __future__ import annotations

import json
from typing import Any

import anyio
import structlog

from app.core.exceptions import BackendUnavailableError
from app.vectorstores.base import SearchHit, VectorRecord, VectorStore

log = structlog.get_logger(__name__)


class MilvusStore(VectorStore):
    name = "milvus"

    def __init__(self, uri: str, collection: str, *, dim: int) -> None:
        self._uri = uri
        self._collection = collection
        self._dim = dim
        self._client: Any | None = None

    def _connect(self) -> Any:
        try:
            from pymilvus import MilvusClient
        except ImportError as exc:  # pragma: no cover
            raise BackendUnavailableError("pymilvus not installed.") from exc

        client = MilvusClient(uri=self._uri)
        if not client.has_collection(self._collection):
            client.create_collection(
                collection_name=self._collection,
                dimension=self._dim,
                metric_type="COSINE",
                auto_id=False,
                primary_field_name="id",
                id_type="string",
                vector_field_name="vector",
                max_length=80,
            )
        return client

    async def ensure_ready(self) -> None:
        if self._client is None:
            self._client = await anyio.to_thread.run_sync(self._connect)

    async def upsert(self, records: list[VectorRecord]) -> int:
        client = self._require_client()
        data = [
            {
                "id": r.id,
                "vector": r.vector,
                "text": r.text,
                "metadata": json.dumps(r.metadata),
            }
            for r in records
        ]
        await anyio.to_thread.run_sync(
            lambda: client.upsert(collection_name=self._collection, data=data)
        )
        return len(data)

    async def query(
        self,
        vector: list[float],
        k: int = 10,
        *,
        filters: dict[str, Any] | None = None,
    ) -> list[SearchHit]:
        client = self._require_client()
        expr = ""
        if filters:
            # Milvus boolean expr over the JSON metadata field, e.g. metadata["k"] == "v".
            parts = [f'metadata["{key}"] == {json.dumps(val)}' for key, val in filters.items()]
            expr = " and ".join(parts)

        res = await anyio.to_thread.run_sync(
            lambda: client.search(
                collection_name=self._collection,
                data=[vector],
                limit=k,
                filter=expr,
                output_fields=["text", "metadata"],
            )
        )
        hits: list[SearchHit] = []
        for row in res[0] if res else []:
            entity = row.get("entity", {})
            raw_meta = entity.get("metadata") or "{}"
            metadata = json.loads(raw_meta) if isinstance(raw_meta, str) else dict(raw_meta)
            hits.append(
                SearchHit(
                    id=str(row["id"]),
                    score=float(row["distance"]),  # COSINE metric: higher is closer
                    text=str(entity.get("text", "")),
                    metadata=metadata,
                )
            )
        return hits

    async def delete(self, ids: list[str]) -> int:
        client = self._require_client()
        await anyio.to_thread.run_sync(
            lambda: client.delete(collection_name=self._collection, ids=ids)
        )
        return len(ids)

    async def count(self) -> int:
        client = self._require_client()
        stats = await anyio.to_thread.run_sync(
            lambda: client.get_collection_stats(self._collection)
        )
        return int(stats.get("row_count", 0))

    async def close(self) -> None:
        if self._client is not None:
            await anyio.to_thread.run_sync(self._client.close)
            self._client = None

    def _require_client(self) -> Any:
        if self._client is None:
            raise BackendUnavailableError("Milvus client not initialized; call ensure_ready().")
        return self._client
