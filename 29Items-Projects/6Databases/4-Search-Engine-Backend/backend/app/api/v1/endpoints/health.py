"""Liveness & readiness probes (unversioned — infrastructure contract, not API)."""

from typing import Any

from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse
from sqlalchemy import text

from app.db.session import async_session_factory

router = APIRouter()


@router.get("/healthz", include_in_schema=False)
async def liveness() -> dict[str, str]:
    return {"status": "ok"}


@router.get("/readyz", include_in_schema=False)
async def readiness(request: Request) -> JSONResponse:
    """Ready = ES reachable (search is the product). Redis/PG degrade gracefully."""
    checks: dict[str, Any] = {}

    try:
        checks["elasticsearch"] = bool(await request.app.state.es.ping())
    except Exception:  # noqa: BLE001
        checks["elasticsearch"] = False

    try:
        await request.app.state.redis.ping()
        checks["redis"] = True
    except Exception:  # noqa: BLE001
        checks["redis"] = False  # degraded: suggest slower, still correct

    try:
        async with async_session_factory() as session:
            await session.execute(text("SELECT 1"))
        checks["postgres"] = True
    except Exception:  # noqa: BLE001
        checks["postgres"] = False  # degraded: catalog writes unavailable

    ready = checks["elasticsearch"]
    return JSONResponse(
        status_code=200 if ready else 503,
        content={"ready": ready, "checks": checks},
    )
