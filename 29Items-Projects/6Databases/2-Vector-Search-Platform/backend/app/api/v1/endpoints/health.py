"""Liveness and readiness probes (used by the ALB target group / ECS)."""

from __future__ import annotations

from fastapi import APIRouter, Request, Response, status
from sqlalchemy import text

from app import __version__
from app.db.session import SessionLocal
from app.vectorstores import get_vector_store

router = APIRouter()


@router.get("/health/live", summary="Liveness probe")
async def live() -> dict[str, str]:
    """Process is up. Cheap and dependency-free."""
    return {"status": "ok", "version": __version__}


async def _check_db() -> bool:
    try:
        async with SessionLocal() as session:
            await session.execute(text("SELECT 1"))
        return True
    except Exception:
        return False


async def _check_redis(request: Request) -> bool | None:
    redis = getattr(request.app.state, "redis", None)
    if redis is None:
        return None  # cache is optional; None => "not configured"
    try:
        return bool(await redis.ping())
    except Exception:
        return False


@router.get("/health/ready", summary="Readiness probe")
async def ready(request: Request, response: Response) -> dict[str, object]:
    """Dependencies reachable. Returns 503 if a required dependency is down."""
    store = get_vector_store()
    db_ok = await _check_db()
    backend_ok = await store.health()
    redis_ok = await _check_redis(request)

    required_ok = db_ok and backend_ok  # redis is optional
    if not required_ok:
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE

    return {
        "status": "ok" if required_ok else "degraded",
        "checks": {
            "database": db_ok,
            "vector_backend": backend_ok,
            "redis": redis_ok,
        },
        "backend": store.name,
        "version": __version__,
    }
