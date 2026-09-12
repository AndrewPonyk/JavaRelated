"""A/B testing endpoints — define experiments and read out results."""
from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, status
from pydantic import BaseModel, Field

from src.api.dependencies import CurrentPrincipal, get_ab_service
from src.services.ab_testing_service import ABTestingService

router = APIRouter(prefix="/ab-tests", tags=["ab-testing"])


class ABTestCreate(BaseModel):
    model_name: str = Field(min_length=1, max_length=128)
    champion_version: int = Field(ge=1)
    challenger_version: int = Field(ge=1)
    challenger_traffic_pct: float = Field(ge=0, le=100, default=10.0)


class ABTestResult(BaseModel):
    test_id: str
    model_name: str
    champion_metric: float
    challenger_metric: float
    p_value: float
    significant: bool


Service = Annotated[ABTestingService, Depends(get_ab_service)]


@router.post("", status_code=status.HTTP_201_CREATED)
async def create_ab_test(
    payload: ABTestCreate, principal: CurrentPrincipal, svc: Service
) -> dict[str, str]:
    principal.require("experiments:write")
    test_id = await svc.create_test(payload.model_dump())
    return {"test_id": test_id}


@router.get("/{test_id}/results", response_model=ABTestResult)
async def get_results(
    test_id: str, principal: CurrentPrincipal, svc: Service
) -> ABTestResult:
    principal.require("experiments:read")
    return ABTestResult(**await svc.evaluate(test_id))
