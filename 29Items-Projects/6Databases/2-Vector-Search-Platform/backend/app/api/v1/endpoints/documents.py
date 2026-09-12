"""Document endpoints: ingest + full CRUD (chunk -> embed -> upsert; read; update; delete)."""

from __future__ import annotations

from fastapi import APIRouter, Depends, Query, Response, status

from app.api.deps import DocumentServiceDep
from app.core.security import require_api_key
from app.models.document import Document
from app.schemas.document import (
    DocumentDetail,
    DocumentIngestRequest,
    DocumentIngestResponse,
    DocumentListResponse,
    DocumentSummary,
    DocumentUpdateRequest,
)

router = APIRouter(dependencies=[Depends(require_api_key)])


def _to_detail(doc: Document, num_chunks: int) -> DocumentDetail:
    return DocumentDetail(
        id=doc.id,
        source=doc.source,
        text=doc.text,
        metadata=doc.meta or {},
        created_at=doc.created_at,
        num_chunks=num_chunks,
    )


def _to_summary(doc: Document, num_chunks: int) -> DocumentSummary:
    return DocumentSummary(
        id=doc.id, source=doc.source, created_at=doc.created_at, num_chunks=num_chunks
    )


@router.post(
    "",
    response_model=DocumentIngestResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Ingest & index a document",
)
async def ingest(
    request: DocumentIngestRequest, service: DocumentServiceDep
) -> DocumentIngestResponse:
    result = await service.ingest(
        request.text, source=request.source, metadata=request.metadata, backend=request.backend
    )
    return DocumentIngestResponse(
        document_id=result.document_id, chunks_indexed=result.chunks_indexed, backend=result.backend
    )


@router.get("", response_model=DocumentListResponse, summary="List documents")
async def list_documents(
    service: DocumentServiceDep,
    limit: int = Query(default=50, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
) -> DocumentListResponse:
    docs, total = await service.list(limit=limit, offset=offset)
    return DocumentListResponse(
        total=total,
        limit=limit,
        offset=offset,
        items=[_to_summary(doc, count) for doc, count in docs],
    )


@router.get("/{document_id}", response_model=DocumentDetail, summary="Get a document")
async def get_document(document_id: str, service: DocumentServiceDep) -> DocumentDetail:
    doc, num_chunks = await service.get_detail(document_id)
    return _to_detail(doc, num_chunks)


@router.put("/{document_id}", response_model=DocumentIngestResponse, summary="Replace & re-index")
async def update_document(
    document_id: str, request: DocumentUpdateRequest, service: DocumentServiceDep
) -> DocumentIngestResponse:
    # ensure it exists first (raises NotFoundError -> 404 via handler)
    await service.get(document_id)
    result = await service.update(
        document_id, text=request.text, metadata=request.metadata, backend=request.backend
    )
    return DocumentIngestResponse(
        document_id=result.document_id, chunks_indexed=result.chunks_indexed, backend=result.backend
    )


@router.delete(
    "/{document_id}", status_code=status.HTTP_204_NO_CONTENT, summary="Delete a document"
)
async def delete_document(document_id: str, service: DocumentServiceDep) -> Response:
    await service.delete(document_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
