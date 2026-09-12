"""Model registry endpoints — list versions and manage stage transitions."""
from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends

from src.api.dependencies import CurrentPrincipal, get_model_registry_service
from src.api.schemas.model import (
    ModelVersionOut,
    StageTransitionRequest,
)
from src.services.model_registry_service import ModelRegistryService

router = APIRouter(prefix="/models", tags=["models"])

Service = Annotated[ModelRegistryService, Depends(get_model_registry_service)]


@router.get("/{name}/versions", response_model=list[ModelVersionOut])
async def list_versions(
    name: str, principal: CurrentPrincipal, svc: Service
) -> list[ModelVersionOut]:
    principal.require("experiments:read")
    return await svc.list_versions(name)


@router.post("/{name}/versions/{version}/stage", response_model=ModelVersionOut)
async def transition_stage(
    name: str,
    version: int,
    payload: StageTransitionRequest,
    principal: CurrentPrincipal,
    svc: Service,
) -> ModelVersionOut:
    """Promote/demote a model version. Production requires an elevated scope."""
    if payload.stage.value == "Production":
        principal.require("models:promote")
    else:
        principal.require("experiments:write")
    return await svc.transition_stage(name, version, payload)
