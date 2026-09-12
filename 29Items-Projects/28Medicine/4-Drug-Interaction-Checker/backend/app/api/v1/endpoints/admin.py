"""Admin / data-ingestion CRUD for the interaction catalog.

All routes require the ``admin`` scope. In production these would be driven by a
governed ETL job; the endpoints exist for curation and tests.
"""

from typing import Annotated

from fastapi import APIRouter, Depends, Query, status

from app.api.deps import get_drug_service, get_interaction_service, require_scopes
from app.models.drug import Drug, DrugClassUpsert, DrugListResponse, DrugUpsert
from app.models.interaction import (
    InteractionListResponse,
    InteractionPair,
    InteractionUpsert,
)
from app.services.drug_service import DrugService
from app.services.interaction_service import InteractionService

router = APIRouter(
    prefix="/admin",
    tags=["admin"],
    dependencies=[Depends(require_scopes("admin"))],
)


# ---- drugs ----------------------------------------------------------------
@router.post("/drugs", response_model=Drug, status_code=status.HTTP_201_CREATED)
async def upsert_drug(
    payload: DrugUpsert,
    service: Annotated[DrugService, Depends(get_drug_service)],
) -> Drug:
    return await service.create(payload)


@router.get("/drugs", response_model=DrugListResponse)
async def list_drugs(
    service: Annotated[DrugService, Depends(get_drug_service)],
    limit: Annotated[int, Query(ge=1, le=200)] = 50,
    offset: Annotated[int, Query(ge=0)] = 0,
) -> DrugListResponse:
    return await service.list(limit=limit, offset=offset)


@router.delete("/drugs/{rxcui}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_drug(
    rxcui: str,
    service: Annotated[DrugService, Depends(get_drug_service)],
) -> None:
    deleted = await service.delete(rxcui)
    if not deleted:
        from app.core.exceptions import DrugNotFoundError

        raise DrugNotFoundError(f"No drug found for rxcui={rxcui}")


@router.post("/classes", status_code=status.HTTP_204_NO_CONTENT)
async def upsert_class(
    payload: DrugClassUpsert,
    service: Annotated[DrugService, Depends(get_drug_service)],
) -> None:
    await service.upsert_class(payload)


# ---- interactions ---------------------------------------------------------
@router.post("/interactions", response_model=InteractionPair, status_code=status.HTTP_201_CREATED)
async def upsert_interaction(
    payload: InteractionUpsert,
    service: Annotated[InteractionService, Depends(get_interaction_service)],
) -> InteractionPair:
    return await service.create_interaction(payload)


@router.get("/interactions", response_model=InteractionListResponse)
async def list_interactions(
    service: Annotated[InteractionService, Depends(get_interaction_service)],
    limit: Annotated[int, Query(ge=1, le=200)] = 50,
    offset: Annotated[int, Query(ge=0)] = 0,
) -> InteractionListResponse:
    return await service.list_interactions(limit=limit, offset=offset)


@router.delete("/interactions/{rxcui_a}/{rxcui_b}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_interaction(
    rxcui_a: str,
    rxcui_b: str,
    service: Annotated[InteractionService, Depends(get_interaction_service)],
) -> None:
    deleted = await service.delete_interaction(rxcui_a, rxcui_b)
    if not deleted:
        from app.core.exceptions import DrugNotFoundError

        raise DrugNotFoundError("No such interaction.")
