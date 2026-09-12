"""Search request/response DTOs."""

from __future__ import annotations

from enum import Enum
from typing import Any

from pydantic import BaseModel, Field

from app.core.config import Backend


class SearchMode(str, Enum):
    vector = "vector"
    keyword = "keyword"
    hybrid = "hybrid"


class SearchRequest(BaseModel):
    query: str = Field(min_length=1, max_length=4_000)
    k: int = Field(default=10, ge=1, le=100)
    backend: Backend | None = None
    mode: SearchMode = SearchMode.hybrid
    filters: dict[str, Any] | None = None


class SearchResult(BaseModel):
    id: str
    score: float
    text: str = ""
    metadata: dict[str, Any] = Field(default_factory=dict)


class SearchResponse(BaseModel):
    query: str
    backend: str
    mode: SearchMode
    count: int
    results: list[SearchResult]
    took_ms: float | None = None
