"""Drug search & lookup endpoints."""

from typing import Annotated

from fastapi import APIRouter, Depends, Query

from app.api.deps import get_drug_service
from app.models.drug import Drug, DrugSearchResult
from app.services.drug_service import DrugService

router = APIRouter(prefix="/drugs", tags=["drugs"])


@router.get("/search", response_model=DrugSearchResult)
async def search_drugs(
    service: Annotated[DrugService, Depends(get_drug_service)],
    q: Annotated[str, Query(min_length=2, description="Drug name to normalize")],
) -> DrugSearchResult:
    return await service.search(q)


@router.get("/{rxcui}", response_model=Drug)
async def get_drug(
    rxcui: str,
    service: Annotated[DrugService, Depends(get_drug_service)],
) -> Drug:
    return await service.get(rxcui)
