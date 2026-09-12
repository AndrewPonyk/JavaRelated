"""Drug-drug interaction check endpoints."""

from typing import Annotated

from fastapi import APIRouter, Depends

from app.api.deps import get_interaction_service
from app.models.drug import DrugInput
from app.models.interaction import InteractionCheckRequest, InteractionCheckResponse
from app.services.interaction_service import InteractionService

router = APIRouter(prefix="/interactions", tags=["interactions"])


@router.post("/check", response_model=InteractionCheckResponse)
async def check_interactions(
    request: InteractionCheckRequest,
    service: Annotated[InteractionService, Depends(get_interaction_service)],
) -> InteractionCheckResponse:
    """Check a set of drugs for pairwise interactions (+ optional ML prediction)."""
    return await service.check(request)


@router.get("/{rxcui_a}/{rxcui_b}", response_model=InteractionCheckResponse)
async def check_pair(
    rxcui_a: str,
    rxcui_b: str,
    service: Annotated[InteractionService, Depends(get_interaction_service)],
) -> InteractionCheckResponse:
    """Convenience pairwise check by RxCUI."""
    request = InteractionCheckRequest(drugs=[DrugInput(rxcui=rxcui_a), DrugInput(rxcui=rxcui_b)])
    return await service.check(request)
