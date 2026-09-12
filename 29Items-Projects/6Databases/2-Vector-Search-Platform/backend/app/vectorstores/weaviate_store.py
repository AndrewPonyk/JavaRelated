"""Weaviate backend adapter (client v4).

Feature-flagged (``ENABLE_WEAVIATE``). We supply our own vectors (vectorizer=none) and use
cosine distance so results are comparable with the other backends. Sync client calls run in
a threadpool; imports are guarded so the app boots without the SDK.
"""

from __future__ import annotations

import uuid
from typing import Any
from urllib.parse import urlparse

import anyio
import structlog

from app.core.exceptions import BackendUnavailableError
from app.vectorstores.base import SearchHit, VectorRecord, VectorStore

log = structlog.get_logger(__name__)


def _deterministic_uuid(record_id: str) -> str:
    # Weaviate object ids must be UUIDs; derive one deterministically from our id.
    return str(uuid.uuid5(uuid.NAMESPACE_URL, record_id))


class WeaviateStore(VectorStore):
    name = "weaviate"

    def __init__(
        self, url: str, *, dim: int, api_key: str | None = None, collection: str = "VspChunk"
    ) -> None:
        parsed = urlparse(url)
        self._host = parsed.hostname or "localhost"
        self._port = parsed.port or 8080
        self._api_key = api_key
        self._dim = dim
        self._collection_name = collection
        self._client: Any | None = None

    def _connect(self) -> Any:
        try:
            import weaviate
            from weaviate.classes.config import Configure, DataType, Property, VectorDistances
        except ImportError as exc:  # pragma: no cover
            raise BackendUnavailableError("weaviate-client not installed.") from exc

        client = weaviate.connect_to_local(host=self._host, port=self._port)
        if not client.collections.exists(self._collection_name):
            client.collections.create(
                name=self._collection_name,
                vectorizer_config=Configure.Vectorizer.none(),
                vector_index_config=Configure.VectorIndex.hnsw(
                    distance_metric=VectorDistances.COSINE
                ),
                properties=[
                    Property(name="text", data_type=DataType.TEXT),
                    Property(name="document_id", data_type=DataType.TEXT),
                    Property(name="chunk_index", data_type=DataType.INT),
                ],
            )
        return client

    async def ensure_ready(self) -> None:
        if self._client is None:
            self._client = await anyio.to_thread.run_sync(self._connect)

    def _collection(self) -> Any:
        return self._require_client().collections.get(self._collection_name)

    async def upsert(self, records: list[VectorRecord]) -> int:
        def _do() -> int:
            collection = self._collection()
            with collection.batch.dynamic() as batch:
                for r in records:
                    batch.add_object(
                        uuid=_deterministic_uuid(r.id),
                        properties={
                            "text": r.text,
                            "document_id": str(r.metadata.get("document_id", "")),
                            "chunk_index": int(r.metadata.get("chunk_index", 0)),
                        },
                        vector=r.vector,
                    )
            return len(records)

        return await anyio.to_thread.run_sync(_do)

    async def query(
        self,
        vector: list[float],
        k: int = 10,
        *,
        filters: dict[str, Any] | None = None,
    ) -> list[SearchHit]:
        def _do() -> list[SearchHit]:
            from weaviate.classes.query import Filter, MetadataQuery

            wfilter = None
            if filters:
                clauses = [Filter.by_property(key).equal(val) for key, val in filters.items()]
                wfilter = clauses[0] if len(clauses) == 1 else Filter.all_of(clauses)

            res = self._collection().query.near_vector(
                near_vector=vector,
                limit=k,
                filters=wfilter,
                return_metadata=MetadataQuery(distance=True),
            )
            hits: list[SearchHit] = []
            for obj in res.objects:
                distance = obj.metadata.distance if obj.metadata else 0.0
                props = dict(obj.properties)
                hits.append(
                    SearchHit(
                        id=str(obj.uuid),
                        score=1.0 - float(distance or 0.0),  # cosine distance -> similarity
                        text=str(props.pop("text", "")),
                        metadata=props,
                    )
                )
            return hits

        return await anyio.to_thread.run_sync(_do)

    async def delete(self, ids: list[str]) -> int:
        def _do() -> int:
            collection = self._collection()
            for record_id in ids:
                collection.data.delete_by_id(_deterministic_uuid(record_id))
            return len(ids)

        return await anyio.to_thread.run_sync(_do)

    async def count(self) -> int:
        def _do() -> int:
            res = self._collection().aggregate.over_all(total_count=True)
            return int(res.total_count or 0)

        return await anyio.to_thread.run_sync(_do)

    async def close(self) -> None:
        if self._client is not None:
            await anyio.to_thread.run_sync(self._client.close)
            self._client = None

    def _require_client(self) -> Any:
        if self._client is None:
            raise BackendUnavailableError("Weaviate client not initialized; call ensure_ready().")
        return self._client
