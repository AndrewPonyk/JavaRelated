"""Pipeline registry CRUD — validation in schemas, logic in the service layer."""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status

from app.schemas.pipeline import PipelineCreate, PipelineRead, PipelineUpdate
from app.services.pipeline_service import (
    DuplicatePipelineNameError,
    PipelineNotFoundError,
    PipelineService,
    get_pipeline_service,
)

router = APIRouter(prefix="/pipelines", tags=["pipelines"])


@router.get("", response_model=list[PipelineRead])
async def list_pipelines(
    limit: int = Query(default=500, ge=1, le=1000),
    service: PipelineService = Depends(get_pipeline_service),
) -> list[PipelineRead]:
    return (await service.list())[:limit]


@router.post("", response_model=PipelineRead, status_code=status.HTTP_201_CREATED)
async def create_pipeline(
    payload: PipelineCreate,
    service: PipelineService = Depends(get_pipeline_service),
) -> PipelineRead:
    try:
        return await service.create(payload)
    except DuplicatePipelineNameError as exc:
        raise HTTPException(status_code=409, detail=f"pipeline name already exists: {exc}") from exc


@router.get("/{pipeline_id}", response_model=PipelineRead)
async def get_pipeline(
    pipeline_id: str,
    service: PipelineService = Depends(get_pipeline_service),
) -> PipelineRead:
    try:
        return await service.get(pipeline_id)
    except PipelineNotFoundError as exc:
        raise HTTPException(status_code=404, detail="pipeline not found") from exc


@router.patch("/{pipeline_id}", response_model=PipelineRead)
async def update_pipeline(
    pipeline_id: str,
    payload: PipelineUpdate,
    service: PipelineService = Depends(get_pipeline_service),
) -> PipelineRead:
    try:
        return await service.update(pipeline_id, payload)
    except PipelineNotFoundError as exc:
        raise HTTPException(status_code=404, detail="pipeline not found") from exc
    except DuplicatePipelineNameError as exc:
        raise HTTPException(status_code=409, detail=f"pipeline name already exists: {exc}") from exc


@router.delete("/{pipeline_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_pipeline(
    pipeline_id: str,
    service: PipelineService = Depends(get_pipeline_service),
) -> Response:
    try:
        await service.delete(pipeline_id)
    except PipelineNotFoundError as exc:
        raise HTTPException(status_code=404, detail="pipeline not found") from exc
    return Response(status_code=status.HTTP_204_NO_CONTENT)
