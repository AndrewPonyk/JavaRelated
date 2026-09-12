"""Kubernetes liveness/readiness probes."""

from __future__ import annotations

from typing import Any

from fastapi import APIRouter

from fraud_detection.db.session import check_connection

router = APIRouter(prefix="/health", tags=["health"])


@router.get("/live")
def liveness() -> dict[str, str]:
    """Liveness probe: the process is up and serving."""
    return {"status": "alive"}


@router.get("/ready")
def readiness() -> dict[str, Any]:
    """Readiness probe with per-component detail.

    Always returns 200 while the process can score traffic - the heuristic
    fallback model guarantees that - and reports degraded components in the
    body (``database: down`` / ``model: fallback``) so operators and probes
    with body inspection can alert on partial degradation.
    """
    database = "up" if check_connection() else "down"
    try:
        from fraud_detection.api.deps import get_model_loader

        model_source = get_model_loader().get_model("champion").source
    except Exception:  # noqa: BLE001 - readiness must never raise
        model_source = "fallback"
    return {"status": "ready", "components": {"database": database, "model": model_source}}
