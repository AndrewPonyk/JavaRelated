"""GET /api/v1/search — faceted product search."""

from fastapi import APIRouter, Depends, Header, Query

from app.api.deps import get_search_service
from app.core.exceptions import InvalidSearchQueryError
from app.schemas.search import SearchResponse, SortOption
from app.search.query_builder import SearchQuery
from app.services.search_service import SearchService

router = APIRouter()


@router.get("", response_model=SearchResponse, summary="Search products")
async def search_products(
    q: str = Query(..., min_length=1, max_length=200, description="Free-text query"),
    category: list[str] | None = Query(default=None, description="Category slugs (repeatable)"),
    brand: list[str] | None = Query(default=None, description="Brands (repeatable)"),
    price_min: float | None = Query(default=None, ge=0),
    price_max: float | None = Query(default=None, ge=0),
    sort: SortOption = Query(default=SortOption.relevance),
    page: int = Query(default=1, ge=1),
    size: int = Query(default=20, ge=1, le=100),
    x_session_id: str | None = Header(default=None, max_length=64),
    service: SearchService = Depends(get_search_service),
) -> SearchResponse:
    if price_min is not None and price_max is not None and price_min > price_max:
        raise InvalidSearchQueryError("price_min must be <= price_max")

    filters: dict[str, list[str]] = {}
    if category:
        filters["category"] = category
    if brand:
        filters["brand"] = brand

    return await service.search(
        SearchQuery(
            query=q,
            filters=filters,
            price_min=price_min,
            price_max=price_max,
            sort=sort,
            page=page,
            size=size,
        ),
        session_id=x_session_id or "anonymous",
    )
