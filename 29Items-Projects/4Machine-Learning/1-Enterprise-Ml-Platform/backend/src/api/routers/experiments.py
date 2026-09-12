"""Experiment tracking endpoints (thin HTTP layer over ExperimentService)."""
from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, status

from src.api.dependencies import CurrentPrincipal, get_experiment_service
from src.api.schemas.common import LimitParam, OffsetParam, Page
from src.api.schemas.experiment import (
    ExperimentCreate,
    ExperimentOut,
    RunOut,
    TrainingRequest,
)
from src.services.experiment_service import ExperimentService

router = APIRouter(prefix="/experiments", tags=["experiments"])

Service = Annotated[ExperimentService, Depends(get_experiment_service)]


@router.post("", response_model=ExperimentOut, status_code=status.HTTP_201_CREATED)
async def create_experiment(
    payload: ExperimentCreate, principal: CurrentPrincipal, svc: Service
) -> ExperimentOut:
    principal.require("experiments:write")
    return await svc.create(payload, owner=principal.subject)


@router.get("", response_model=Page[ExperimentOut])
async def list_experiments(
    principal: CurrentPrincipal,
    svc: Service,
    limit: LimitParam = 50,
    offset: OffsetParam = 0,
) -> Page[ExperimentOut]:
    principal.require("experiments:read")
    items, total = await svc.list(limit=limit, offset=offset)
    return Page(items=items, total=total, limit=limit, offset=offset)


@router.get("/{experiment_id}", response_model=ExperimentOut)
async def get_experiment(
    experiment_id: str, principal: CurrentPrincipal, svc: Service
) -> ExperimentOut:
    principal.require("experiments:read")
    return await svc.get(experiment_id)


@router.post(
    "/{experiment_id}/train",
    response_model=RunOut,
    status_code=status.HTTP_202_ACCEPTED,
)
async def start_training(
    experiment_id: str,
    payload: TrainingRequest,
    principal: CurrentPrincipal,
    svc: Service,
) -> RunOut:
    """Submit a training run (Kubeflow / SageMaker in production)."""
    principal.require("experiments:write")
    return await svc.start_training(experiment_id, payload)
