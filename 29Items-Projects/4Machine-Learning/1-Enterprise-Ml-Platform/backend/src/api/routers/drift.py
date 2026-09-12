"""Drift detection endpoints — trigger checks and read drift reports."""
from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from src.api.dependencies import CurrentPrincipal, get_drift_service
from src.services.drift_service import DriftService

router = APIRouter(prefix="/drift", tags=["drift"])


class DriftReport(BaseModel):
    model_name: str
    method: str  # "PSI" | "KS" | "KL"
    score: float
    threshold: float
    drifted: bool
    evaluated_features: int


Service = Annotated[DriftService, Depends(get_drift_service)]


@router.post("/{model_name}/check", response_model=DriftReport)
async def run_drift_check(
    model_name: str, principal: CurrentPrincipal, svc: Service
) -> DriftReport:
    """Run an on-demand drift evaluation against the model's baseline snapshot."""
    principal.require("experiments:read")
    return DriftReport(**await svc.run_check(model_name))


@router.get("/{model_name}/latest", response_model=DriftReport)
async def latest_report(
    model_name: str, principal: CurrentPrincipal, svc: Service
) -> DriftReport:
    principal.require("experiments:read")
    return DriftReport(**await svc.latest(model_name))
