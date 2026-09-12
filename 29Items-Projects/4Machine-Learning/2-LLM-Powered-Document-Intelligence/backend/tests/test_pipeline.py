"""Integration tests for the RAG pipeline against the local backend (no mocks)."""

from __future__ import annotations

import pytest

from app.rag.pipeline import RagPipeline
from app.rag.vector_store import reset_in_memory_store

CONTRACT = (
    "This Master Services Agreement is entered into by Acme Corp and the Client. "
    "The termination clause states that either party may terminate this agreement "
    "with thirty days written notice. Payment is due within fifteen days of invoice. "
    "Confidential information must not be disclosed to third parties."
)


@pytest.mark.asyncio
async def test_ingest_then_answer_is_grounded() -> None:
    reset_in_memory_store()
    pipe = RagPipeline()
    n = await pipe.ingest_bytes(
        tenant_id="t1",
        document_id="doc1",
        doc_type="legal",
        content=CONTRACT.encode(),
        filename="msa.txt",
    )
    assert n >= 1

    answer = await pipe.answer(tenant_id="t1", question="What is the termination notice period?")
    assert answer.citations, "answer should cite at least one source chunk"
    assert "thirty days" in answer.text.lower() or "notice" in answer.text.lower()
    assert answer.citations[0].document_id == "doc1"


@pytest.mark.asyncio
async def test_answer_says_dont_know_without_context() -> None:
    reset_in_memory_store()
    pipe = RagPipeline()
    answer = await pipe.answer(tenant_id="empty-tenant", question="What is the refund policy?")
    assert answer.citations == []
    assert "don't know" in answer.text.lower()


@pytest.mark.asyncio
async def test_tenant_isolation_in_pipeline() -> None:
    reset_in_memory_store()
    pipe = RagPipeline()
    await pipe.ingest_bytes(
        tenant_id="tenant-a",
        document_id="doc1",
        doc_type="legal",
        content=CONTRACT.encode(),
        filename="msa.txt",
    )
    # A different tenant must not retrieve tenant-a's content.
    answer = await pipe.answer(tenant_id="tenant-b", question="termination notice period")
    assert answer.citations == []


@pytest.mark.asyncio
async def test_summarize_document() -> None:
    reset_in_memory_store()
    pipe = RagPipeline()
    await pipe.ingest_bytes(
        tenant_id="t1",
        document_id="doc1",
        doc_type="legal",
        content=CONTRACT.encode(),
        filename="msa.txt",
    )
    summary = await pipe.summarize_document(tenant_id="t1", document_id="doc1")
    assert summary and summary != "I don't know based on the provided documents."


@pytest.mark.asyncio
async def test_streaming_yields_tokens_then_citations() -> None:
    reset_in_memory_store()
    pipe = RagPipeline()
    await pipe.ingest_bytes(
        tenant_id="t1",
        document_id="doc1",
        doc_type="legal",
        content=CONTRACT.encode(),
        filename="msa.txt",
    )
    kinds = []
    async for kind, _payload in pipe.astream_answer(tenant_id="t1", question="termination notice"):
        kinds.append(kind)
    assert "token" in kinds
    assert kinds[-1] == "citations"
