"""GET /api/v1/suggest — autocomplete suggestions (Redis-cached completion suggester)."""

from fastapi import APIRouter, Depends, Query

from app.api.deps import get_suggest_service
from app.schemas.suggest import SuggestResponse
from app.services.suggest_service import SuggestService

router = APIRouter()


@router.get("", response_model=SuggestResponse, summary="Autocomplete suggestions")
async def suggest(
    q: str = Query(..., min_length=1, max_length=100, description="Typed prefix"),
    limit: int = Query(default=8, ge=1, le=20),
    service: SuggestService = Depends(get_suggest_service),
) -> SuggestResponse:
    suggestions = await service.suggest(prefix=q, limit=limit)
    return SuggestResponse(query=q, suggestions=suggestions)
