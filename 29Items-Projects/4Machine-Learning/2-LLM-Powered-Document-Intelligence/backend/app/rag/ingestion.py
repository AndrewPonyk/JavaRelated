"""Ingestion entrypoint used by the inline path and the SQS worker.

Fetches raw bytes from storage and runs them through the pipeline. Idempotent on
``document_id`` (see :meth:`RagPipeline.ingest_bytes`).
"""

from __future__ import annotations

import logging

from app.rag.pipeline import get_pipeline
from app.storage import get_storage

logger = logging.getLogger(__name__)


async def ingest_from_storage(
    *, document_id: str, tenant_id: str, s3_key: str, filename: str, doc_type: str
) -> int:
    """Load a document from storage and index it. Returns the chunk count."""
    logger.info(
        "ingest start",
        extra={"extra_fields": {"document_id": document_id, "tenant_id": tenant_id}},
    )
    content = await get_storage().get(s3_key)
    count = await get_pipeline().ingest_bytes(
        tenant_id=tenant_id,
        document_id=document_id,
        doc_type=doc_type,
        content=content,
        filename=filename,
    )
    logger.info(
        "ingest done",
        extra={"extra_fields": {"document_id": document_id, "chunk_count": count}},
    )
    return count
