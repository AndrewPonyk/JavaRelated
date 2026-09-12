"""Pharmacy-system integration: authenticated batch interaction checks.

Treat the payload as sensitive (PHI-adjacent): pass an opaque ``patient_ref``
and never log patient identifiers.
"""

from typing import Annotated

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field

from app.api.deps import get_interaction_service, require_scopes
from app.models.drug import DrugInput
from app.models.interaction import InteractionCheckRequest, InteractionCheckResponse
from app.services.interaction_service import InteractionService

router = APIRouter(prefix="/pharmacy", tags=["pharmacy"])


class MedicationOrder(BaseModel):
    ndc: str | None = None
    rxcui: str | None = None
    name: str | None = None


class PharmacyBatchRequest(BaseModel):
    patient_ref: str = Field(..., description="Opaque patient reference (no PHI)")
    medications: list[MedicationOrder] = Field(..., min_length=1)
    include_ml_prediction: bool = True


@router.post("/check-batch", response_model=InteractionCheckResponse)
async def check_batch(
    request: PharmacyBatchRequest,
    service: Annotated[InteractionService, Depends(get_interaction_service)],
    principal: Annotated[dict, Depends(require_scopes("pharmacy:check"))],
) -> InteractionCheckResponse:
    check = InteractionCheckRequest(
        drugs=[DrugInput(rxcui=m.rxcui, ndc=m.ndc, name=m.name) for m in request.medications],
        include_ml_prediction=request.include_ml_prediction,
    )
    return await service.check(check)
