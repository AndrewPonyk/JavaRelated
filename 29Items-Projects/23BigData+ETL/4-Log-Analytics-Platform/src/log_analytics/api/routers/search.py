"""Log search endpoint — a constrained query surface over OpenSearch (never DSL passthrough)."""

from __future__ import annotations

from datetime import datetime
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Query

from log_analytics.api.deps import get_search_service
from log_analytics.api.schemas import LogSearchResponse
from log_analytics.api.services.search_service import SearchBackendError, SearchService

router = APIRouter(prefix="/logs", tags=["logs"])


@router.get("/search", response_model=LogSearchResponse)
async def search_logs(
    service_dep: Annotated[SearchService, Depends(get_search_service)],
    q: Annotated[str | None, Query(max_length=512, description="full-text over message")] = None,
    service: Annotated[str | None, Query(max_length=128)] = None,
    level: Annotated[str | None, Query(pattern="^(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)$")] = None,
    from_ts: Annotated[datetime | None, Query(description="ISO-8601 lower bound")] = None,
    to_ts: Annotated[datetime | None, Query(description="ISO-8601 upper bound")] = None,
    size: Annotated[int, Query(ge=1, le=500)] = 50,
    offset: Annotated[int, Query(ge=0, le=10_000)] = 0,
) -> LogSearchResponse:
    """Search recent logs. Results sorted by @timestamp desc.

    TODO: enforce per-team `service` visibility from the caller's auth claims.
    TODO: search_after pagination for deep scrolling (offset capped at 10k by ES anyway).
    """
    try:
        return await service_dep.search_logs(
            query=q,
            service=service,
            level=level,
            from_ts=from_ts,
            to_ts=to_ts,
            size=size,
            offset=offset,
        )
    except SearchBackendError as exc:
        raise HTTPException(status_code=502, detail=f"search backend error: {exc}") from exc
