"""Dataset endpoints — thin HTTP layer over dataset_service.

Domain errors (not-found, duplicate, Trino unavailable) are raised by the
service layer and translated to problem responses by handlers in app.main.
Reads require a valid principal (any role); mutations require a writer role.
"""

from __future__ import annotations

import uuid
from typing import Literal

from fastapi import APIRouter, Depends, Query, Response, status
from sqlalchemy.orm import Session

from app.core.security import Principal, get_current_principal, require_writer
from app.db.session import get_db
from app.schemas.dataset import (
    DatasetCreate,
    DatasetList,
    DatasetRead,
    DatasetUpdate,
    DatasetVersionCreate,
    DatasetVersionRead,
    PreviewResponse,
)
from app.services import dataset_service
from app.services.trino_client import TrinoPreviewService, get_preview_service

router = APIRouter(
    prefix="/datasets", tags=["datasets"], dependencies=[Depends(get_current_principal)]
)


@router.get("", response_model=DatasetList)
def list_datasets(
    layer: Literal["bronze", "silver", "gold"] | None = None,
    name: str | None = Query(default=None, max_length=120, description="Exact-name lookup"),
    limit: int = Query(default=50, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
    db: Session = Depends(get_db),
) -> DatasetList:
    items, total = dataset_service.list_datasets(
        db, layer=layer, name=name, limit=limit, offset=offset
    )
    return DatasetList(
        items=[DatasetRead.model_validate(item) for item in items],
        total=total,
        limit=limit,
        offset=offset,
    )


@router.get("/{dataset_id}", response_model=DatasetRead)
def get_dataset(dataset_id: uuid.UUID, db: Session = Depends(get_db)) -> DatasetRead:
    return DatasetRead.model_validate(dataset_service.get_dataset(db, dataset_id))


@router.post("", response_model=DatasetRead, status_code=status.HTTP_201_CREATED)
def create_dataset(
    payload: DatasetCreate,
    db: Session = Depends(get_db),
    principal: Principal = Depends(require_writer),
) -> DatasetRead:
    created = dataset_service.create_dataset(db, payload, actor=principal.subject)
    return DatasetRead.model_validate(created)


@router.patch("/{dataset_id}", response_model=DatasetRead)
def update_dataset(
    dataset_id: uuid.UUID,
    payload: DatasetUpdate,
    db: Session = Depends(get_db),
    principal: Principal = Depends(require_writer),
) -> DatasetRead:
    updated = dataset_service.update_dataset(db, dataset_id, payload, actor=principal.subject)
    return DatasetRead.model_validate(updated)


@router.delete("/{dataset_id}", status_code=status.HTTP_204_NO_CONTENT, response_class=Response)
def delete_dataset(
    dataset_id: uuid.UUID,
    db: Session = Depends(get_db),
    principal: Principal = Depends(require_writer),
) -> Response:
    dataset_service.delete_dataset(db, dataset_id, actor=principal.subject)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


# --- Schema versions -----------------------------------------------------------


@router.get("/{dataset_id}/versions", response_model=list[DatasetVersionRead])
def list_versions(dataset_id: uuid.UUID, db: Session = Depends(get_db)) -> list[DatasetVersionRead]:
    versions = dataset_service.list_dataset_versions(db, dataset_id)
    return [DatasetVersionRead.model_validate(v) for v in versions]


@router.post(
    "/{dataset_id}/versions",
    response_model=DatasetVersionRead,
    status_code=status.HTTP_201_CREATED,
)
def add_version(
    dataset_id: uuid.UUID,
    payload: DatasetVersionCreate,
    db: Session = Depends(get_db),
    principal: Principal = Depends(require_writer),
) -> DatasetVersionRead:
    version = dataset_service.add_dataset_version(db, dataset_id, payload, actor=principal.subject)
    return DatasetVersionRead.model_validate(version)


# --- Preview (read-only, LIMIT-capped, via Trino) --------------------------------


@router.get("/{dataset_id}/preview", response_model=PreviewResponse)
def preview_dataset(
    dataset_id: uuid.UUID,
    limit: int = Query(default=20, ge=1, le=100),
    db: Session = Depends(get_db),
    preview_service: TrinoPreviewService = Depends(get_preview_service),
) -> PreviewResponse:
    dataset = dataset_service.get_dataset(db, dataset_id)
    result = preview_service.preview(dataset, limit=limit)
    return PreviewResponse(
        columns=result.columns,
        rows=result.rows,
        row_count=len(result.rows),
        source=result.source,
    )
