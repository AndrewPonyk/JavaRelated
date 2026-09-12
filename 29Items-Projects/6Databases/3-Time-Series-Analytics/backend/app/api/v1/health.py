"""Liveness/readiness probes (compose healthchecks + LB target checks)."""

from __future__ import annotations

from fastapi import APIRouter, Response

from app import __version__
from app.db import BackendUnavailableError, cassandra
from app.db import influx as influx_db
from app.db import redis as redis_db

router = APIRouter()


@router.get("/live")
async def live() -> dict:
    """Process is up. Never checks dependencies — a down backend must not
    make the orchestrator restart-loop the API (degraded mode is deliberate)."""
    return {"status": "ok", "version": __version__}


@router.get("/ready")
async def ready(response: Response) -> dict:
    checks: dict[str, str] = {}

    try:
        cassandra.get_session()
        checks["cassandra"] = "ok"
    except BackendUnavailableError:
        checks["cassandra"] = "down"

    try:
        await redis_db.get_client().ping()
        checks["redis"] = "ok"
    except Exception:
        checks["redis"] = "down"

    # Telemetry plane is optional for readiness — report, don't gate.
    checks["influxdb"] = "ok" if influx_db.is_connected() else "down"

    degraded = checks["cassandra"] != "ok" or checks["redis"] != "ok"
    if degraded:
        response.status_code = 503
    return {"status": "degraded" if degraded else "ok", "checks": checks}
