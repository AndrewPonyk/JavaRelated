"""Service-level tests for RAGService (real local backend + test DB session)."""

from __future__ import annotations

import pytest
from sqlalchemy import select

from app.models.query_log import QueryLog
from app.rag.pipeline import get_pipeline
from app.services.rag_service import RAGService


async def _seed(tenant: str, document_id: str = "doc1") -> None:
    await get_pipeline().ingest_bytes(
        tenant_id=tenant,
        document_id=document_id,
        doc_type="legal",
        content=b"The termination clause requires thirty days written notice.",
        filename="msa.txt",
    )


@pytest.mark.asyncio
async def test_answer_persists_query_log_with_citations(db_session) -> None:
    await _seed("tenant-a")
    svc = RAGService(db_session)
    resp = await svc.answer_question(
        tenant_id="tenant-a", question="termination notice period", doc_type=None, top_k=4
    )

    assert resp.citations  # property, not exact LLM string
    assert resp.model_id  # local-extractive-qa
    assert resp.latency_ms >= 0

    rows = (await db_session.execute(select(QueryLog))).scalars().all()
    assert len(rows) == 1
    assert rows[0].tenant_id == "tenant-a"
    assert rows[0].id == resp.query_id


@pytest.mark.asyncio
async def test_feedback_updates_log(db_session) -> None:
    await _seed("tenant-a")
    svc = RAGService(db_session)
    resp = await svc.answer_question(
        tenant_id="tenant-a", question="termination", doc_type=None, top_k=4
    )

    ok = await svc.record_feedback(tenant_id="tenant-a", query_id=resp.query_id, value=1)
    assert ok is True

    row = await db_session.get(QueryLog, resp.query_id)
    assert row.feedback == 1


@pytest.mark.asyncio
async def test_feedback_missing_query_returns_false(db_session) -> None:
    svc = RAGService(db_session)
    assert await svc.record_feedback(tenant_id="tenant-a", query_id="nope", value=1) is False
