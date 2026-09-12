"""Health, liveness, and readiness probes."""

from fastapi import APIRouter

from app import __version__
from app.db.neo4j_client import verify_connectivity
from app.models.common import HealthStatus

router = APIRouter(tags=["health"])


@router.get("/health", response_model=HealthStatus)
async def health() -> HealthStatus:
    connected = await verify_connectivity()
    return HealthStatus(
        status="ok" if connected else "degraded",
        version=__version__,
        neo4j_connected=connected,
    )


@router.get("/health/live")
async def liveness() -> dict:
    """Liveness: process is up (no dependency checks)."""
    return {"status": "ok"}


@router.get("/health/ready")
async def readiness() -> dict:
    """Readiness: gate traffic until Neo4j connectivity is verified."""
    connected = await verify_connectivity()
    return {"status": "ready" if connected else "not_ready"}
