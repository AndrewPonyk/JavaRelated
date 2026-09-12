"""Liveness & readiness endpoints (ALB target-group checks point here)."""

from __future__ import annotations

from typing import Annotated

import httpx
from fastapi import APIRouter, Depends, Response

from log_analytics.common.config import Settings, get_settings

router = APIRouter(tags=["health"])


async def opensearch_status(
    url: str,
    auth: tuple[str, str] | None = None,
    transport: httpx.AsyncBaseTransport | None = None,
) -> str:
    """Cluster health color (green/yellow/red) or 'unreachable'/'error'."""
    try:
        async with httpx.AsyncClient(timeout=2.0, auth=auth, transport=transport) as client:
            resp = await client.get(f"{url}/_cluster/health")
    except httpx.HTTPError:
        return "unreachable"
    if resp.status_code != 200:
        return "error"
    return str(resp.json().get("status", "unknown"))


@router.get("/healthz")
async def healthz() -> dict[str, str]:
    """Liveness: the process is up. No dependencies checked — never flaps."""
    return {"status": "ok"}


@router.get("/readyz")
async def readyz(
    settings: Annotated[Settings, Depends(get_settings)],
    response: Response,
) -> dict[str, object]:
    """Readiness: can we actually serve queries? Checks OpenSearch reachability."""
    opensearch = await opensearch_status(settings.opensearch_url, settings.opensearch_auth)
    ready = opensearch in {"green", "yellow"}
    if not ready:
        response.status_code = 503
    return {"status": "ready" if ready else "degraded", "components": {"opensearch": opensearch}}
