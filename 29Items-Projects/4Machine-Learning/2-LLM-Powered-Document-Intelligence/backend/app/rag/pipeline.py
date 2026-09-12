"""The RAG pipeline — provider-agnostic orchestration.

Composes the embedder + vector store + chat model (whichever backend is configured) into
the high-level operations the service layer needs: ingest, answer, stream-answer,
summarize, delete. This is the single place the retrieval → synthesis flow lives, matching
the sequence diagrams in ARCHITECTURE §2.3.
"""

from __future__ import annotations

from collections.abc import AsyncIterator

from app.core.config import settings
from app.rag.chunking import split_document
from app.rag.embeddings import get_embedder
from app.rag.llm import SYSTEM_PROMPT, get_chat_model
from app.rag.parsing import parse_bytes
from app.rag.types import Answer, RetrievedChunk, VectorRecord
from app.rag.vector_store import get_vector_store


def format_prompt(question: str, chunks: list[RetrievedChunk]) -> str:
    """Assemble the user prompt: untrusted context first, then the question."""
    context = "\n\n".join(f"[source: {c.document_id}#{c.chunk_index}]\n{c.text}" for c in chunks)
    return f"Context:\n{context}\n\nQuestion: {question}"


class RagPipeline:
    """Stateless orchestrator over the configured RAG providers."""

    def __init__(self) -> None:
        self._embedder = get_embedder()
        self._store = get_vector_store()
        self._chat = get_chat_model()

    @property
    def model_id(self) -> str:
        return self._chat.model_id

    # ── Ingestion ──────────────────────────────────────────────────────────
    async def ingest_bytes(
        self, *, tenant_id: str, document_id: str, doc_type: str, content: bytes, filename: str
    ) -> int:
        """Parse → chunk → embed → upsert. Returns the number of chunks indexed.

        Idempotent on ``document_id``: chunk ids are ``{document_id}:{i}`` so re-ingesting
        overwrites rather than duplicates.
        """
        text = parse_bytes(content, filename)
        chunks = split_document(
            text,
            base_metadata={
                "document_id": document_id,
                "doc_type": doc_type,
                "tenant_id": tenant_id,
            },
        )
        if not chunks:
            return 0
        vectors = await self._embedder.aembed_documents([c.text for c in chunks])
        records = [
            VectorRecord(
                id=f"{document_id}:{i}",
                vector=vectors[i],
                text=chunks[i].text,
                metadata=chunks[i].metadata,
            )
            for i in range(len(chunks))
        ]
        await self._store.aupsert(tenant_id, records)
        return len(records)

    async def delete_document(self, *, tenant_id: str, document_id: str) -> None:
        await self._store.adelete_document(tenant_id, document_id)

    # ── Retrieval ──────────────────────────────────────────────────────────
    async def _retrieve(
        self, *, tenant_id: str, question: str, doc_type: str | None, top_k: int | None
    ) -> list[RetrievedChunk]:
        query_vec = await self._embedder.aembed_query(question)
        flt = {"doc_type": doc_type} if doc_type else None
        return await self._store.aquery(tenant_id, query_vec, top_k or settings.rag_top_k, flt)

    # ── Answering ──────────────────────────────────────────────────────────
    async def answer(
        self,
        *,
        tenant_id: str,
        question: str,
        doc_type: str | None = None,
        top_k: int | None = None,
    ) -> Answer:
        chunks = await self._retrieve(
            tenant_id=tenant_id, question=question, doc_type=doc_type, top_k=top_k
        )
        prompt = format_prompt(question, chunks)
        text = await self._chat.agenerate(SYSTEM_PROMPT, prompt)
        return Answer(text=text, citations=chunks, model_id=self.model_id)

    async def astream_answer(
        self,
        *,
        tenant_id: str,
        question: str,
        doc_type: str | None = None,
        top_k: int | None = None,
    ) -> AsyncIterator[tuple[str, object]]:
        """Yield ``("token", str)`` deltas, then a final ``("citations", list[dict])``."""
        chunks = await self._retrieve(
            tenant_id=tenant_id, question=question, doc_type=doc_type, top_k=top_k
        )
        prompt = format_prompt(question, chunks)
        async for token in self._chat.astream(SYSTEM_PROMPT, prompt):
            yield "token", token
        yield (
            "citations",
            [
                {
                    "document_id": c.document_id,
                    "chunk_index": c.chunk_index,
                    "score": round(c.score, 4),
                }
                for c in chunks
            ],
        )

    # ── Summarization ──────────────────────────────────────────────────────
    async def summarize_document(self, *, tenant_id: str, document_id: str) -> str:
        """Summarize a single indexed document from its chunks (map-reduce style)."""
        # Retrieve this document's chunks via a broad query scoped by document_id.
        probe = await self._embedder.aembed_query("summary overview key points")
        chunks = await self._store.aquery(
            tenant_id, probe, top_k=50, flt={"document_id": document_id}
        )
        if not chunks:
            return ""
        chunks.sort(key=lambda c: c.chunk_index)
        combined = "\n\n".join(c.text for c in chunks)
        prompt = format_prompt(
            "__summarize__ Summarize this document.",
            [
                RetrievedChunk(
                    id="combined", text=combined, score=1.0, metadata={"document_id": document_id}
                )
            ],
        )
        return await self._chat.agenerate(SYSTEM_PROMPT, prompt)


def get_pipeline() -> RagPipeline:
    """Construct a pipeline bound to the currently-configured providers."""
    return RagPipeline()
