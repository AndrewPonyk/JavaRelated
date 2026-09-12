"""Liveness/readiness probes for the ALB and ECS.

- `/healthz` — liveness: the process serves requests.
- `/readyz`  — readiness: Postgres must answer (503 otherwise); Trino is
  reported but non-fatal, because core catalog CRUD works without it
  (previews degrade to a 502 problem response).
"""

from __future__ import annotations

import logging

import httpx
from fastapi import APIRouter, Depends
from fastapi.responses import JSONResponse
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.db.session import get_db

router = APIRouter(tags=["health"])
log = logging.getLogger("api.health")


@router.get("/healthz")
def healthz() -> dict:
    return {"status": "ok"}


def _check_trino() -> str:
    settings = get_settings()
    url = f"{settings.trino_http_scheme}://{settings.trino_host}:{settings.trino_port}/v1/info"
    try:
        response = httpx.get(url, timeout=1.5)
        return "ok" if response.status_code == 200 else f"http {response.status_code}"
    except httpx.HTTPError:
        return "unreachable"


@router.get("/readyz")
def readyz(db: Session = Depends(get_db)) -> JSONResponse:
    checks: dict[str, str] = {}

    try:
        db.execute(text("SELECT 1"))
        checks["database"] = "ok"
    except Exception:  # noqa: BLE001 — any DB failure means not ready
        log.warning("readiness: database check failed", exc_info=True)
        checks["database"] = "unavailable"

    checks["trino"] = _check_trino()

    ready = checks["database"] == "ok"
    return JSONResponse(
        status_code=200 if ready else 503,
        content={"status": "ready" if ready else "degraded", "checks": checks},
    )
