"""Search API contract (mirrored by frontend/src/types/search.ts — keep in sync)."""

from enum import Enum
from uuid import UUID

from pydantic import BaseModel

from app.schemas.common import PageMeta


class SortOption(str, Enum):
    relevance = "relevance"
    price_asc = "price_asc"
    price_desc = "price_desc"
    newest = "newest"


class ProductHit(BaseModel):
    id: UUID
    sku: str
    name: str
    brand: str | None = None
    price: float
    category_slug: str | None = None
    in_stock: bool = True
    score: float | None = None


class FacetValue(BaseModel):
    value: str
    count: int
    selected: bool = False


class FacetGroup(BaseModel):
    name: str
    label: str
    values: list[FacetValue]


class SearchResponse(BaseModel):
    query: str
    hits: list[ProductHit]
    facets: list[FacetGroup]
    meta: PageMeta
    took_ms: int
