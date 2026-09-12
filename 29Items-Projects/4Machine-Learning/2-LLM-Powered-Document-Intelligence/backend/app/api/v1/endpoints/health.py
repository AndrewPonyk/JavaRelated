"""Health / readiness probes for load balancers and orchestrators."""

from __future__ import annotations

import logging

from fastapi import APIRouter, Response, status
from sqlalchemy import text

from app import __version__
from app.api.deps import SessionDep

router = APIRouter()
logger = logging.getLogger(__name__)


@router.get("/health")
async def health() -> dict:
    """Liveness: the process is up. Cheap, no dependencies."""
    return {"status": "ok", "version": __version__}


@router.get("/ready")
async def ready(session: SessionDep, response: Response) -> dict:
    """Readiness: required dependencies are reachable.

    Checks database connectivity (``SELECT 1``). Returns 503 if the DB is unreachable so
    traffic isn't routed to an instance that can't serve. Vector store / Bedrock are checked
    lazily on first use and surface as 502 domain errors.
    """
    try:
        await session.execute(text("SELECT 1"))
    except Exception:  # noqa: BLE001 — any DB failure means not ready
        logger.exception("readiness check failed: database unreachable")
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE
        return {"status": "not ready", "database": "unreachable"}
    return {"status": "ready", "database": "ok"}
