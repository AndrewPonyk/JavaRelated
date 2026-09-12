"""Ingestion worker (production).

Long-polls SQS for ingestion jobs, runs each through the pipeline, and updates the
document's status. Runs the same Docker image as the API with this module as entrypoint::

    python -m app.worker

Idempotent and crash-safe: a re-delivered message re-indexes cleanly; after
``maxReceiveCount`` failures SQS routes the message to the DLQ for inspection.
"""

from __future__ import annotations

import asyncio
import logging

from app.core.config import settings
from app.core.logging import configure_logging
from app.db.session import SessionLocal
from app.queue import IngestionJob, get_queue
from app.rag.errors import RAGError
from app.rag.ingestion import ingest_from_storage
from app.services.document_service import DocumentService

logger = logging.getLogger(__name__)


async def process_job(job: IngestionJob) -> None:
    """Ingest one document and update its status in its own transaction."""
    async with SessionLocal() as session:
        service = DocumentService(session)
        try:
            count = await ingest_from_storage(
                document_id=job.document_id,
                tenant_id=job.tenant_id,
                s3_key=job.s3_key,
                filename=job.filename,
                doc_type=job.doc_type,
            )
            await service.mark_indexed(document_id=job.document_id, chunk_count=count)
            await session.commit()
        except RAGError as exc:
            logger.error(
                "ingest failed: %s", exc, extra={"extra_fields": {"document_id": job.document_id}}
            )
            await service.mark_failed(document_id=job.document_id)
            await session.commit()
            raise  # leave the message un-acked → SQS retry / DLQ


async def run() -> None:
    configure_logging(settings.log_level)
    queue = get_queue()
    logger.info(
        "ingestion worker started", extra={"extra_fields": {"queue": settings.sqs_queue_url}}
    )
    while True:
        messages = await asyncio.to_thread(queue.receive)
        for receipt, job in messages:
            try:
                await process_job(job)
                await asyncio.to_thread(queue.ack, receipt)
            except Exception:  # noqa: BLE001 — keep the loop alive; SQS handles retry/DLQ
                logger.exception("job processing error")


if __name__ == "__main__":
    asyncio.run(run())
