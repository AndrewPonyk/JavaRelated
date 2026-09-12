"""Document CRUD + summarization endpoints.

Endpoints are declarative: validation via FastAPI/Pydantic + explicit guards, scope
checks via the principal, orchestration delegated to DocumentService.
"""

from __future__ import annotations

from fastapi import APIRouter, File, Form, HTTPException, Query, Response, UploadFile, status

from app.api.deps import DocumentServiceDep, PrincipalDep
from app.core.config import settings
from app.schemas.document import DocumentList, DocumentOut, DocumentUploadResponse
from app.schemas.query import SummaryResponse

router = APIRouter()

ALLOWED_DOC_TYPES = {"legal", "medical", "general"}


def _require(principal, scope: str) -> None:
    if not principal.has_scope(scope):
        raise HTTPException(status.HTTP_403_FORBIDDEN, f"missing required scope: {scope}")


@router.post("", response_model=DocumentUploadResponse, status_code=status.HTTP_202_ACCEPTED)
async def upload_document(
    principal: PrincipalDep,
    service: DocumentServiceDep,
    file: UploadFile = File(...),
    doc_type: str = Form("general"),
) -> DocumentUploadResponse:
    """Upload a document. Indexed inline (dev) or asynchronously (prod)."""
    _require(principal, "documents:write")
    if doc_type not in ALLOWED_DOC_TYPES:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, f"invalid doc_type: {doc_type}")

    content = await file.read()
    if not content:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "empty file")
    if len(content) > settings.max_upload_bytes:
        raise HTTPException(status.HTTP_413_REQUEST_ENTITY_TOO_LARGE, "file too large")

    doc = await service.create(
        tenant_id=principal.tenant_id,
        filename=file.filename or "untitled",
        content=content,
        doc_type=doc_type,
    )
    return DocumentUploadResponse(document_id=doc.id, status=doc.status.value)


@router.get("", response_model=DocumentList)
async def list_documents(
    principal: PrincipalDep,
    service: DocumentServiceDep,
    limit: int = Query(default=50, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
) -> DocumentList:
    """List the calling tenant's documents (most recent first), paginated."""
    _require(principal, "documents:read")
    docs, total = await service.list_for_tenant(
        tenant_id=principal.tenant_id, limit=limit, offset=offset
    )
    return DocumentList(
        items=[DocumentOut.model_validate(d) for d in docs],
        total=total,
        limit=limit,
        offset=offset,
    )


@router.get("/{document_id}", response_model=DocumentOut)
async def get_document(
    document_id: str, principal: PrincipalDep, service: DocumentServiceDep
) -> DocumentOut:
    """Fetch one document's status/metadata (tenant-scoped)."""
    _require(principal, "documents:read")
    doc = await service.get(tenant_id=principal.tenant_id, document_id=document_id)
    if doc is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "document not found")
    return DocumentOut.model_validate(doc)


@router.delete(
    "/{document_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    response_class=Response,
)
async def delete_document(
    document_id: str, principal: PrincipalDep, service: DocumentServiceDep
) -> Response:
    """Delete a document and its vectors (tenant-scoped)."""
    _require(principal, "documents:write")
    deleted = await service.delete(tenant_id=principal.tenant_id, document_id=document_id)
    if not deleted:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "document not found")
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/{document_id}/summarize", response_model=SummaryResponse)
async def summarize_document(
    document_id: str, principal: PrincipalDep, service: DocumentServiceDep
) -> SummaryResponse:
    """Summarize an indexed document."""
    _require(principal, "documents:read")
    summary = await service.summarize(tenant_id=principal.tenant_id, document_id=document_id)
    if summary is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "document not found")
    return SummaryResponse(document_id=document_id, summary=summary)
