"""Unified serving gateway — single inference surface across frameworks.

Resolves the A/B variant, runs prediction (in-cluster predictor or SageMaker
endpoint), and fires async drift logging without blocking the response.
"""
from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, BackgroundTasks, Depends

from src.api.dependencies import (
    CurrentPrincipal,
    get_ab_service,
    get_drift_service,
    get_serving_service,
)
from src.api.schemas.model import PredictionRequest, PredictionResponse
from src.services.ab_testing_service import ABTestingService
from src.services.drift_service import DriftService
from src.services.serving_service import ServingService

router = APIRouter(prefix="/serving", tags=["serving"])


@router.post("/{model_name}/predict", response_model=PredictionResponse)
async def predict(
    model_name: str,
    payload: PredictionRequest,
    principal: CurrentPrincipal,
    background: BackgroundTasks,
    serving: Annotated[ServingService, Depends(get_serving_service)],
    ab: Annotated[ABTestingService, Depends(get_ab_service)],
    drift: Annotated[DriftService, Depends(get_drift_service)],
) -> PredictionResponse:
    principal.require("serving:invoke")
    variant = await ab.resolve_variant(model_name, payload.subject_id)
    result = await serving.predict(model_name, variant, payload.features)
    # Fire-and-forget: never let drift logging add latency to the hot path.
    background.add_task(drift.log_inference, model_name, payload.features, result.prediction)
    return result
