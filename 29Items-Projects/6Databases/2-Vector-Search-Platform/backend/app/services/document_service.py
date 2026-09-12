"""Document lifecycle: ingest (chunk -> embed -> persist -> index) and CRUD.

Postgres (via the repository) is the system of record; the vector store holds the embeddings.
Both are kept consistent: create indexes vectors, delete removes them, update re-indexes.
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass

import structlog

from app.core.config import Backend, Settings, get_settings
from app.core.exceptions import NotFoundError
from app.models.document import Document, DocumentChunk
from app.repositories.document_repository import DocumentRepository
from app.services.chunking import chunk_text
from app.services.embedding_service import EmbeddingService
from app.vectorstores import VectorRecord, get_vector_store

log = structlog.get_logger(__name__)


@dataclass(slots=True)
class IngestResult:
    document_id: str
    chunks_indexed: int
    backend: str


class DocumentService:
    def __init__(
        self,
        repository: DocumentRepository,
        embedder: EmbeddingService,
        settings: Settings | None = None,
    ) -> None:
        self._repo = repository
        self._embedder = embedder
        self._settings = settings or get_settings()

    async def ingest(
        self,
        text: str,
        *,
        source: str | None = None,
        metadata: dict | None = None,
        backend: Backend | str | None = None,
        document_id: str | None = None,
    ) -> IngestResult:
        metadata = metadata or {}
        document_id = document_id or str(uuid.uuid4())
        chunks = chunk_text(
            text, size=self._settings.chunk_size, overlap=self._settings.chunk_overlap
        )
        if not chunks:
            raise ValueError("Document text produced no chunks.")

        vectors = await self._embedder.embed_batch(chunks)

        document = Document(id=document_id, source=source, text=text, meta=metadata)
        records: list[VectorRecord] = []
        for i, (chunk, vector) in enumerate(zip(chunks, vectors, strict=True)):
            chunk_id = f"{document_id}:{i}"
            document.chunks.append(
                DocumentChunk(
                    id=chunk_id,
                    document_id=document_id,
                    chunk_index=i,
                    text=chunk,
                    meta={"source": source, **metadata},
                )
            )
            records.append(
                VectorRecord(
                    id=chunk_id,
                    vector=vector,
                    text=chunk,
                    metadata={
                        "document_id": document_id,
                        "chunk_index": i,
                        "source": source,
                        **metadata,
                    },
                )
            )

        await self._repo.create(document)

        store = get_vector_store(backend)
        await store.ensure_ready()
        indexed = await store.upsert(records)

        log.info("document.ingested", document_id=document_id, chunks=indexed, backend=store.name)
        return IngestResult(document_id=document_id, chunks_indexed=indexed, backend=store.name)

    async def get(self, document_id: str) -> Document:
        document = await self._repo.get(document_id)
        if document is None:
            raise NotFoundError(f"Document {document_id} not found.")
        return document

    async def get_detail(self, document_id: str) -> tuple[Document, int]:
        """Return a document plus its chunk count (no chunk rows loaded)."""
        document = await self.get(document_id)
        return document, await self._repo.count_chunks(document_id)

    async def list(
        self, *, limit: int = 50, offset: int = 0
    ) -> tuple[list[tuple[Document, int]], int]:
        return await self._repo.list_with_counts(limit=limit, offset=offset)

    async def delete(self, document_id: str, *, backend: Backend | str | None = None) -> None:
        await self.get(document_id)  # raises NotFoundError -> 404 if missing
        chunk_ids = await self._repo.chunk_ids(document_id)

        store = get_vector_store(backend)
        await store.ensure_ready()
        if chunk_ids:
            await store.delete(chunk_ids)

        await self._repo.delete(document_id)
        log.info("document.deleted", document_id=document_id, backend=store.name)

    async def update(
        self,
        document_id: str,
        *,
        text: str,
        metadata: dict | None = None,
        backend: Backend | str | None = None,
    ) -> IngestResult:
        """Replace a document's content and re-index it, **preserving its id**."""
        existing = await self.get(document_id)
        source = existing.source
        await self.delete(document_id, backend=backend)
        # Re-ingest under the same id keeps a single, consistent embed + index code path.
        return await self.ingest(
            text, source=source, metadata=metadata, backend=backend, document_id=document_id
        )
