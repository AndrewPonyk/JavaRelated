"""FastAPI application factory — the backend-for-frontend (BFF).

REST CRUD + read endpoints + a WebSocket live PnL feed. OpenAPI is generated
automatically at ``/docs`` and ``/openapi.json``.
"""

from __future__ import annotations

import asyncio
import contextlib
import os
from collections.abc import AsyncIterator

from fastapi import FastAPI, HTTPException, Request, WebSocket, WebSocketDisconnect, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy import text
from sqlalchemy.ext.asyncio import async_sessionmaker

from api_gateway.routers import portfolio, strategies
from api_gateway.services.portfolio_service import PortfolioService
from trading_common.db import create_all, make_engine, make_session_factory
from trading_common.utils import configure_logging, get_logger, load_settings

log = get_logger(component="api-gateway")

_INSECURE_DEFAULT_SECRET = "dev-insecure-secret-change-in-prod"  # noqa: S105


def create_app(
    *,
    session_factory: async_sessionmaker | None = None,
    auth_enabled: bool | None = None,
) -> FastAPI:
    settings = load_settings("api-gateway")
    resolved_auth = (
        auth_enabled
        if auth_enabled is not None
        else os.getenv("AUTH_ENABLED", "false").lower() == "true"
    )

    @contextlib.asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        configure_logging(settings.service_name, settings.env, settings.log_level)
        # Fail fast on insecure prod configuration.
        if (
            resolved_auth
            and settings.env != "dev"
            and app.state.jwt_secret == _INSECURE_DEFAULT_SECRET
        ):
            raise RuntimeError("JWT_SECRET must be set when AUTH_ENABLED in non-dev environments")
        engine = None
        if session_factory is not None:
            app.state.session_factory = session_factory  # injected (tests)
        else:
            engine = make_engine(settings.database_url)
            # SQLite dev DB is created from ORM metadata; Postgres uses db/migrations.
            if settings.database_url.startswith("sqlite"):
                await create_all(engine)
            app.state.session_factory = make_session_factory(engine)
        log.info("api.startup", env=settings.env, auth=resolved_auth)
        try:
            yield
        finally:
            if engine is not None:
                await engine.dispose()
            log.info("api.shutdown")

    app = FastAPI(title="Algorithmic Trading Platform API", version="0.1.0", lifespan=lifespan)
    app.state.auth_enabled = resolved_auth
    app.state.jwt_secret = os.getenv("JWT_SECRET", _INSECURE_DEFAULT_SECRET)
    app.state.jwt_algorithm = os.getenv("JWT_ALGORITHM", "HS256")
    app.state.jwt_audience = os.getenv("JWT_AUDIENCE") or None

    # Compress large JSON responses.
    app.add_middleware(GZipMiddleware, minimum_size=512)

    allowed = settings.extra.get("cors_allowed_origins", ["http://localhost:5173"])
    app.add_middleware(
        CORSMiddleware,
        allow_origins=allowed,
        allow_credentials=True,
        allow_methods=["GET", "POST", "PATCH", "DELETE"],
        allow_headers=["Authorization", "Content-Type", "Idempotency-Key"],
    )

    @app.middleware("http")
    async def security_headers(request: Request, call_next):
        response = await call_next(request)
        response.headers.setdefault("X-Content-Type-Options", "nosniff")
        response.headers.setdefault("X-Frame-Options", "DENY")
        response.headers.setdefault("Referrer-Policy", "no-referrer")
        return response

    @app.exception_handler(Exception)
    async def unhandled_exception_handler(request: Request, exc: Exception) -> JSONResponse:
        # Log the detail server-side; never leak internals to the client.
        log.exception("api.unhandled_error", path=request.url.path, method=request.method)
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={"detail": "internal server error"},
        )

    app.include_router(strategies.router, prefix="/api/v1")
    app.include_router(portfolio.router, prefix="/api/v1")

    @app.get("/health", tags=["ops"])
    async def health() -> dict[str, str]:
        """Liveness probe."""
        return {"status": "ok"}

    @app.get("/health/ready", tags=["ops"])
    async def health_ready() -> dict[str, str]:
        """Readiness probe — verifies database connectivity."""
        factory = getattr(app.state, "session_factory", None)
        if factory is None:
            raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "database not configured")
        try:
            async with factory() as session:
                await session.execute(text("SELECT 1"))
        except Exception:
            raise HTTPException(
                status.HTTP_503_SERVICE_UNAVAILABLE, "database unavailable"
            ) from None
        return {"status": "ready"}

    @app.websocket("/api/v1/ws/pnl")
    async def ws_pnl(websocket: WebSocket) -> None:
        """Stream PnL snapshots to the dashboard (~1 Hz)."""
        await websocket.accept()
        factory = websocket.app.state.session_factory
        try:
            while True:
                async with factory() as session:
                    summary = await PortfolioService(session).pnl_summary()
                await websocket.send_json(summary.model_dump(mode="json"))
                await asyncio.sleep(1.0)
        except WebSocketDisconnect:
            return

    return app


# Entry point for `uvicorn api_gateway.main:app`.
app = create_app()
