"""Document orchestration: upload, list, get, delete, summarize.

The service owns all side effects (storage, DB, queue) so endpoints stay thin and the
same logic is reusable from the worker. All operations are tenant-scoped.

Ingestion mode:
  * ``inline`` (local/dev) — parse + index synchronously on upload; the document is
    INDEXED by the time ``create`` returns.
  * ``queue``  (prod) — store the bytes, enqueue an SQS job, return PENDING; the worker
    indexes and flips the status.
"""

from __future__ import annotations

import logging

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.models.document import Document, DocumentStatus
from app.queue import IngestionJob, get_queue
from app.rag.errors import RAGError
from app.rag.pipeline import get_pipeline
from app.storage import get_storage

logger = logging.getLogger(__name__)


class DocumentService:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def create(
        self, *, tenant_id: str, filename: str, content: bytes, doc_type: str
    ) -> Document:
        """Persist a document and ingest it (inline) or enqueue ingestion (queue)."""
        s3_key = f"{tenant_id}/{filename}"
        await get_storage().put(s3_key, content)

        doc = Document(
            tenant_id=tenant_id,
            filename=filename,
            s3_key=s3_key,
            doc_type=doc_type,
            status=DocumentStatus.PENDING,
        )
        self._session.add(doc)
        await self._session.flush()  # populate doc.id
        logger.info("document created", extra={"extra_fields": {"document_id": doc.id}})

        if settings.ingest_mode == "inline":
            try:
                count = await get_pipeline().ingest_bytes(
                    tenant_id=tenant_id,
                    document_id=doc.id,
                    doc_type=doc_type,
                    content=content,
                    filename=filename,
                )
                doc.status = DocumentStatus.INDEXED
                doc.chunk_count = count
            except RAGError as exc:
                logger.error("inline ingest failed: %s", exc)
                doc.status = DocumentStatus.FAILED
        else:
            get_queue().enqueue(
                IngestionJob(
                    document_id=doc.id,
                    tenant_id=tenant_id,
                    s3_key=s3_key,
                    filename=filename,
                    doc_type=doc_type,
                )
            )
        await self._session.flush()
        return doc

    async def list_for_tenant(
        self, *, tenant_id: str, limit: int = 50, offset: int = 0
    ) -> tuple[list[Document], int]:
        """Return a page of the tenant's documents and the total count."""
        total = await self._session.scalar(
            select(func.count()).select_from(Document).where(Document.tenant_id == tenant_id)
        )
        result = await self._session.execute(
            select(Document)
            .where(Document.tenant_id == tenant_id)
            .order_by(Document.created_at.desc())
            .limit(limit)
            .offset(offset)
        )
        return list(result.scalars().all()), int(total or 0)

    async def get(self, *, tenant_id: str, document_id: str) -> Document | None:
        result = await self._session.execute(
            select(Document).where(Document.id == document_id, Document.tenant_id == tenant_id)
        )
        return result.scalar_one_or_none()

    async def delete(self, *, tenant_id: str, document_id: str) -> bool:
        """Delete a document: vectors, stored bytes, and the DB row. Returns False if absent."""
        doc = await self.get(tenant_id=tenant_id, document_id=document_id)
        if doc is None:
            return False
        await get_pipeline().delete_document(tenant_id=tenant_id, document_id=document_id)
        await get_storage().delete(doc.s3_key)
        await self._session.delete(doc)
        await self._session.flush()
        return True

    async def summarize(self, *, tenant_id: str, document_id: str) -> str | None:
        """Return a summary of an indexed document, or None if it doesn't exist."""
        doc = await self.get(tenant_id=tenant_id, document_id=document_id)
        if doc is None:
            return None
        return await get_pipeline().summarize_document(tenant_id=tenant_id, document_id=document_id)

    # ── Worker-facing status transitions ──────────────────────────────────
    async def mark_indexed(self, *, document_id: str, chunk_count: int) -> None:
        doc = await self._session.get(Document, document_id)
        if doc:
            doc.status = DocumentStatus.INDEXED
            doc.chunk_count = chunk_count

    async def mark_failed(self, *, document_id: str) -> None:
        doc = await self._session.get(Document, document_id)
        if doc:
            doc.status = DocumentStatus.FAILED
