"""Liveness and readiness probes (mounted at the app root, not under /api/v1)."""

from __future__ import annotations

import asyncio
import contextlib

from fastapi import APIRouter
from fastapi.responses import JSONResponse
from sqlalchemy import text

from app.core.config import get_settings

router = APIRouter(tags=["health"])

_CHECK_TIMEOUT_SECONDS = 1.5


@router.get("/healthz")
def healthz() -> dict[str, str]:
    """Liveness: process is up. Never checks dependencies."""
    return {"status": "ok", "version": "0.1.0"}


@router.get("/readyz")
async def readyz() -> JSONResponse:
    """Readiness: gates load-balancer traffic. Checks Postgres and Redis with
    short timeouts and reports per-dependency status (503 on any failure)."""
    database_status, redis_status = await asyncio.gather(_check_database(), _check_redis())
    statuses = {"database": database_status, "redis": redis_status}
    healthy = all(value == "ok" for value in statuses.values())
    return JSONResponse(
        status_code=200 if healthy else 503,
        content={"status": "ok" if healthy else "degraded", **statuses},
    )


async def _check_database() -> str:
    from app.db.session import get_engine

    try:
        async with asyncio.timeout(_CHECK_TIMEOUT_SECONDS):
            async with get_engine().connect() as connection:
                await connection.execute(text("SELECT 1"))
        return "ok"
    except Exception as exc:  # - probe: any failure is "down"
        return f"error: {type(exc).__name__}"


async def _check_redis() -> str:
    import redis.asyncio as aioredis

    client = aioredis.from_url(
        get_settings().redis_url,
        socket_timeout=_CHECK_TIMEOUT_SECONDS,
        socket_connect_timeout=_CHECK_TIMEOUT_SECONDS,
    )
    try:
        async with asyncio.timeout(_CHECK_TIMEOUT_SECONDS):
            await client.ping()
        return "ok"
    except Exception as exc:  # - probe: any failure is "down"
        return f"error: {type(exc).__name__}"
    finally:
        with contextlib.suppress(Exception):  # best-effort cleanup
            await client.aclose()
