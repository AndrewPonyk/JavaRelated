"""RAG Q&A endpoints: ask (JSON or SSE stream) + feedback."""

from __future__ import annotations

from fastapi import APIRouter, HTTPException, Response, status
from fastapi.responses import StreamingResponse

from app.api.deps import PrincipalDep, RAGServiceDep
from app.schemas.query import FeedbackRequest, QueryRequest, QueryResponse

router = APIRouter()


def _require(principal, scope: str) -> None:
    if not principal.has_scope(scope):
        raise HTTPException(status.HTTP_403_FORBIDDEN, f"missing required scope: {scope}")


@router.post("", response_model=QueryResponse)
async def ask(
    payload: QueryRequest, principal: PrincipalDep, service: RAGServiceDep
) -> QueryResponse:
    """Answer a question over the tenant's indexed documents, with citations."""
    _require(principal, "query:run")
    return await service.answer_question(
        tenant_id=principal.tenant_id,
        question=payload.question,
        doc_type=payload.doc_type,
        top_k=payload.top_k,
    )


@router.post("/stream")
async def ask_stream(
    payload: QueryRequest, principal: PrincipalDep, service: RAGServiceDep
) -> StreamingResponse:
    """Stream the answer as Server-Sent Events (token deltas → citations → done)."""
    _require(principal, "query:run")
    generator = service.stream_answer(
        tenant_id=principal.tenant_id,
        question=payload.question,
        doc_type=payload.doc_type,
        top_k=payload.top_k,
    )
    return StreamingResponse(
        generator,
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@router.post(
    "/{query_id}/feedback",
    status_code=status.HTTP_204_NO_CONTENT,
    response_class=Response,
)
async def submit_feedback(
    query_id: str, payload: FeedbackRequest, principal: PrincipalDep, service: RAGServiceDep
) -> Response:
    """Record thumbs up/down on a prior answer (feeds the eval dataset)."""
    _require(principal, "query:run")
    ok = await service.record_feedback(
        tenant_id=principal.tenant_id, query_id=query_id, value=payload.value
    )
    if not ok:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "query not found")
    return Response(status_code=status.HTTP_204_NO_CONTENT)
