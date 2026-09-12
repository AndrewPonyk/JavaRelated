"""FastAPI application factory and ASGI entrypoint.

Run locally with:  ``uvicorn app.main:app --reload``
"""

from __future__ import annotations

import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy import text

from app.api.v1.router import api_router
from app.core import cache
from app.core.config import get_settings
from app.core.exceptions import register_exception_handlers
from app.core.logging import configure_logging, request_id_ctx
from app.core.rate_limit import RateLimitMiddleware

settings = get_settings()


@asynccontextmanager
async def lifespan(_: FastAPI):
    configure_logging(settings.log_level)
    yield


def create_app() -> FastAPI:
    app = FastAPI(
        title=settings.app_name,
        version="0.1.0",
        description="Portfolio optimizer: MPT, VaR/CVaR, Sharpe, Monte Carlo.",
        lifespan=lifespan,
    )

    # Middleware execution order (outermost first): CORS -> request-id ->
    # rate-limit -> gzip -> routes. add_middleware prepends, so add inner first.
    app.add_middleware(GZipMiddleware, minimum_size=1024)

    if settings.rate_limit_enabled:
        app.add_middleware(RateLimitMiddleware, limit_per_minute=settings.rate_limit_per_minute)

    @app.middleware("http")
    async def attach_request_id(request: Request, call_next):
        """Generate/propagate a request id, bind it to logs, set security headers."""
        request_id = request.headers.get("x-request-id", str(uuid.uuid4()))
        token = request_id_ctx.set(request_id)
        try:
            response = await call_next(request)
        finally:
            request_id_ctx.reset(token)
        response.headers["x-request-id"] = request_id
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["X-Frame-Options"] = "DENY"
        response.headers["Referrer-Policy"] = "no-referrer"
        if settings.is_production:
            response.headers["Strict-Transport-Security"] = "max-age=63072000; includeSubDomains"
        return response

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.backend_cors_origins,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    register_exception_handlers(app)
    app.include_router(api_router, prefix=settings.api_v1_prefix)

    @app.get("/healthz", tags=["health"])
    def healthz() -> dict[str, str]:
        """Liveness probe for the ALB / ECS."""
        return {"status": "ok"}

    @app.get("/readyz", tags=["health"])
    def readyz():
        """Readiness probe: DB is required; Redis is reported best-effort."""
        from app.db.session import engine

        db_ok = False
        try:
            with engine.connect() as conn:
                conn.execute(text("SELECT 1"))
            db_ok = True
        except Exception:  # noqa: BLE001
            db_ok = False

        redis_ok = cache.ping()
        payload = {
            "status": "ready" if db_ok else "degraded",
            "database": "ok" if db_ok else "unavailable",
            "redis": "ok" if redis_ok else "unavailable",
        }
        return JSONResponse(status_code=200 if db_ok else 503, content=payload)

    return app


app = create_app()
