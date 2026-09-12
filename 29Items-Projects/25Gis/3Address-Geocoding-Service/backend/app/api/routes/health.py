import logging

from fastapi import APIRouter, Response, status
from sqlalchemy import text

from app.core.config import settings
from app.db.session import engine

logger = logging.getLogger(__name__)

router = APIRouter()


@router.get("/health")
async def health_check() -> dict[str, str]:
    """Liveness probe: the process is up. Does not touch dependencies."""
    return {"status": "ok", "environment": settings.app_env}


@router.get("/health/ready")
async def readiness_check(response: Response) -> dict[str, str]:
    """Readiness probe: verifies the PostGIS connection is usable."""
    try:
        async with engine.connect() as connection:
            await connection.execute(text("SELECT 1"))
    except Exception as exc:  # pragma: no cover - exercised via integration env
        logger.warning("readiness check failed", extra={"error": str(exc)})
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE
        return {"status": "not_ready", "database": "unavailable"}
    return {"status": "ready", "database": "ok"}
