"""Liveness/readiness probes for ECS + ALB health checks."""

from __future__ import annotations

from fastapi import APIRouter
from sqlalchemy import text

from app.api.deps import DbSession, Storage

router = APIRouter()


@router.get("/live", summary="Liveness probe")
async def live() -> dict[str, str]:
    return {"status": "ok"}


@router.get("/ready", summary="Readiness probe")
async def ready(session: DbSession, storage: Storage) -> dict[str, str]:
    """Verify DB connectivity + object-store reachability before reporting ready."""
    checks: dict[str, str] = {}
    try:
        await session.execute(text("SELECT 1"))
        checks["database"] = "ok"
    except Exception:  # noqa: BLE001 - readiness must report, not raise
        checks["database"] = "down"
    try:
        storage.ensure_buckets()
        checks["storage"] = "ok"
    except Exception:  # noqa: BLE001
        checks["storage"] = "down"
    checks["status"] = "ready" if set(checks.values()) <= {"ok"} else "degraded"
    return checks
