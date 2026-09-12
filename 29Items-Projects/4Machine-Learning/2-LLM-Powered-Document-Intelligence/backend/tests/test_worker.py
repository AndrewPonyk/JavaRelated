"""Tests for the ingestion worker's job processing (queue-mode path).

Exercises ``worker.process_job`` end-to-end against the local storage + in-memory vector
store, with the worker's own ``SessionLocal`` pointed at the per-test SQLite DB.
"""

from __future__ import annotations

import pytest

from app import worker
from app.models.document import Document, DocumentStatus
from app.queue import IngestionJob
from app.storage import get_storage


@pytest.mark.asyncio
async def test_process_job_marks_indexed(_sessionmaker, monkeypatch) -> None:
    monkeypatch.setattr(worker, "SessionLocal", lambda: _sessionmaker())

    await get_storage().put(
        "tenant-a/doc.txt", b"The termination clause requires thirty days written notice."
    )
    async with _sessionmaker() as s:
        s.add(
            Document(
                id="docw1",
                tenant_id="tenant-a",
                filename="doc.txt",
                s3_key="tenant-a/doc.txt",
                doc_type="legal",
                status=DocumentStatus.PENDING,
            )
        )
        await s.commit()

    job = IngestionJob(
        document_id="docw1",
        tenant_id="tenant-a",
        s3_key="tenant-a/doc.txt",
        filename="doc.txt",
        doc_type="legal",
    )
    await worker.process_job(job)

    async with _sessionmaker() as s:
        doc = await s.get(Document, "docw1")
        assert doc.status == DocumentStatus.INDEXED
        assert doc.chunk_count >= 1


@pytest.mark.asyncio
async def test_process_job_marks_failed_on_missing_object(_sessionmaker, monkeypatch) -> None:
    from app.rag.errors import RAGError

    monkeypatch.setattr(worker, "SessionLocal", lambda: _sessionmaker())
    async with _sessionmaker() as s:
        s.add(
            Document(
                id="docw2",
                tenant_id="tenant-a",
                filename="missing.txt",
                s3_key="tenant-a/missing.txt",
                doc_type="legal",
                status=DocumentStatus.PENDING,
            )
        )
        await s.commit()

    job = IngestionJob(
        document_id="docw2",
        tenant_id="tenant-a",
        s3_key="tenant-a/missing.txt",  # not in storage
        filename="missing.txt",
        doc_type="legal",
    )
    with pytest.raises(RAGError):
        await worker.process_job(job)

    async with _sessionmaker() as s:
        doc = await s.get(Document, "docw2")
        assert doc.status == DocumentStatus.FAILED
