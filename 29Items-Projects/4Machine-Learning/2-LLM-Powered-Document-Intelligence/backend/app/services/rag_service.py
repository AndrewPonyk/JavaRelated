"""RAG orchestration: run the pipeline, persist the query log, return a cited answer.

Free of FastAPI types so it can be unit-tested with a fake session and the local backend.
"""

from __future__ import annotations

import json
import logging
import time
from collections.abc import AsyncIterator

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.query_log import QueryLog
from app.rag.pipeline import get_pipeline
from app.schemas.query import Citation, QueryResponse

logger = logging.getLogger(__name__)


def _to_citations(chunks) -> list[Citation]:
    return [
        Citation(document_id=c.document_id, chunk_index=c.chunk_index, score=round(c.score, 4))
        for c in chunks
    ]


class RAGService:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session
        self._pipeline = get_pipeline()

    async def answer_question(
        self, *, tenant_id: str, question: str, doc_type: str | None, top_k: int | None
    ) -> QueryResponse:
        """Retrieve context, synthesize an answer, and persist the query log."""
        started = time.perf_counter()
        answer = await self._pipeline.answer(
            tenant_id=tenant_id, question=question, doc_type=doc_type, top_k=top_k
        )
        citations = _to_citations(answer.citations)
        latency_ms = int((time.perf_counter() - started) * 1000)

        log = QueryLog(
            tenant_id=tenant_id,
            question=question,
            answer=answer.text,
            citations=[c.model_dump() for c in citations],
            model_id=answer.model_id,
            latency_ms=latency_ms,
        )
        self._session.add(log)
        await self._session.flush()

        return QueryResponse(
            query_id=log.id,
            answer=answer.text,
            citations=citations,
            model_id=answer.model_id,
            latency_ms=latency_ms,
        )

    async def stream_answer(
        self, *, tenant_id: str, question: str, doc_type: str | None, top_k: int | None
    ) -> AsyncIterator[str]:
        """Yield Server-Sent Events: token deltas, then a citations event, then done.

        The full answer is persisted to the query log once streaming completes.
        """
        started = time.perf_counter()
        parts: list[str] = []
        citations: list[dict] = []
        async for kind, payload in self._pipeline.astream_answer(
            tenant_id=tenant_id, question=question, doc_type=doc_type, top_k=top_k
        ):
            if kind == "token":
                parts.append(str(payload))
                yield _sse("token", {"text": payload})
            elif kind == "citations":
                citations = list(payload)  # type: ignore[arg-type]
                yield _sse("citations", {"citations": citations})

        answer_text = "".join(parts)
        latency_ms = int((time.perf_counter() - started) * 1000)
        self._session.add(
            QueryLog(
                tenant_id=tenant_id,
                question=question,
                answer=answer_text,
                citations=citations,
                model_id=self._pipeline.model_id,
                latency_ms=latency_ms,
            )
        )
        await self._session.flush()
        yield _sse("done", {"latency_ms": latency_ms})

    async def record_feedback(self, *, tenant_id: str, query_id: str, value: int) -> bool:
        """Capture thumbs up/down (-1/0/+1). Returns False if the log isn't found."""
        result = await self._session.execute(
            select(QueryLog).where(QueryLog.id == query_id, QueryLog.tenant_id == tenant_id)
        )
        log = result.scalar_one_or_none()
        if log is None:
            return False
        log.feedback = value
        await self._session.flush()
        return True


def _sse(event: str, data: dict) -> str:
    return f"event: {event}\ndata: {json.dumps(data)}\n\n"
