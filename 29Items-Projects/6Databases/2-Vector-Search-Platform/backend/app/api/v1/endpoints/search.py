"""Search endpoints (vector / keyword / hybrid)."""

from __future__ import annotations

import time

from fastapi import APIRouter, Depends

from app.api.deps import SearchServiceDep, SettingsDep
from app.core.security import require_api_key
from app.schemas.search import SearchRequest, SearchResponse

router = APIRouter(dependencies=[Depends(require_api_key)])


@router.post("", response_model=SearchResponse, summary="Search documents")
async def search(
    request: SearchRequest, service: SearchServiceDep, settings: SettingsDep
) -> SearchResponse:
    """Run a semantic / keyword / hybrid search against the chosen backend."""
    started = time.perf_counter()
    results = await service.search(
        request.query,
        k=request.k,
        backend=request.backend,
        mode=request.mode,
        filters=request.filters,
    )
    took_ms = (time.perf_counter() - started) * 1000.0
    backend_label = request.backend.value if request.backend else settings.default_backend.value
    return SearchResponse(
        query=request.query,
        backend=backend_label,
        mode=request.mode,
        count=len(results),
        results=results,
        took_ms=round(took_ms, 2),
    )
